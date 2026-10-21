package com.truthguard.service;

import com.truthguard.dto.pipeline.AiPipelineRequest;
import com.truthguard.dto.pipeline.AiPipelineResponse;
import com.truthguard.dto.query.QuestionQueryRequest;
import com.truthguard.dto.query.QuestionQueryResponse;
import com.truthguard.dto.query.QueryStageTraceDto;
import com.truthguard.dto.resolution.ResolutionRequest;
import com.truthguard.dto.resolution.ResolutionResponse;
import com.truthguard.dto.resolution.ResolutionStatus;
import com.truthguard.exception.AiServiceException;
import com.truthguard.exception.AiServiceTimeoutException;
import com.truthguard.exception.AiServiceUnavailableException;
import com.truthguard.exception.QueryNotFoundException;
import com.truthguard.model.QueryEntity;
import com.truthguard.model.QueryStageTraceEntity;
import com.truthguard.model.QueryStatus;
import com.truthguard.model.StageStatus;
import com.truthguard.repository.QueryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * End-to-end question answering pipeline orchestrator for TruthGuard.
 */
@Service
public class QueryOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(QueryOrchestrationService.class);

    private final QueryRepository queryRepository;
    private final AgentPipelineClient agentPipelineClient;
    private final ResolutionService resolutionService;

    public QueryOrchestrationService(
            QueryRepository queryRepository,
            AgentPipelineClient agentPipelineClient,
            ResolutionService resolutionService
    ) {
        this.queryRepository = queryRepository;
        this.agentPipelineClient = agentPipelineClient;
        this.resolutionService = resolutionService;
    }

    public QuestionQueryResponse processQuery(QuestionQueryRequest request) {
        long startTime = System.currentTimeMillis();
        String correlationId = (request.getCorrelationId() != null && !request.getCorrelationId().isBlank())
                ? request.getCorrelationId().trim()
                : "tg-" + UUID.randomUUID().toString().substring(0, 8);

        MDC.put("correlationId", correlationId);
        try {
            log.info("[{}] Initiating QA orchestration for query: '{}'", correlationId, request.getQuestion());

            // 1. Create and persist initial query entity
            UUID queryId = UUID.randomUUID();
            Instant createdAt = Instant.now();
            QueryEntity query = new QueryEntity(queryId, correlationId, request.getQuestion().trim(), QueryStatus.PROCESSING, createdAt);

            // Stage 1: Request Validation
            long tVal = System.currentTimeMillis();
            if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
                throw new IllegalArgumentException("Question cannot be empty");
            }
            query.addStageTrace("REQUEST_VALIDATION", StageStatus.SUCCESS, System.currentTimeMillis() - tVal, "Question syntax and parameters verified");
            query = queryRepository.save(query);

            // 2. Call Python AI Service (Retrieval + Synthesis + Critic)
            long tAi = System.currentTimeMillis();
            AiPipelineResponse aiResponse;
            try {
                AiPipelineRequest aiReq = new AiPipelineRequest(request.getQuestion().trim(), correlationId);
                aiReq.setTopK(request.getTopK() != null ? request.getTopK() : 5);
                aiReq.setMinScore(request.getMinScore() != null ? request.getMinScore() : 0.0);
                aiReq.setSemanticWeight(request.getSemanticWeight() != null ? request.getSemanticWeight() : 0.7);

                Map<String, Object> filters = new HashMap<>();
                if (request.getDocumentIds() != null && !request.getDocumentIds().isEmpty()) {
                    filters.put("document_ids", request.getDocumentIds());
                }
                if (request.getVersionIds() != null && !request.getVersionIds().isEmpty()) {
                    filters.put("version_ids", request.getVersionIds());
                }
                if (request.getAuthorityLevels() != null && !request.getAuthorityLevels().isEmpty()) {
                    filters.put("authority_levels", request.getAuthorityLevels());
                }
                if (!filters.isEmpty()) {
                    aiReq.setFilters(filters);
                }

                aiResponse = agentPipelineClient.executePipeline(aiReq);
                long aiDuration = System.currentTimeMillis() - tAi;

                int passageCount = aiResponse.getPassages() != null ? aiResponse.getPassages().size() : 0;
                int claimCount = (aiResponse.getSynthesizerOutput() != null && aiResponse.getSynthesizerOutput().getClaims() != null)
                        ? aiResponse.getSynthesizerOutput().getClaims().size()
                        : 0;
                String criticVerdict = (aiResponse.getCriticOutput() != null && aiResponse.getCriticOutput().getVerdict() != null)
                        ? aiResponse.getCriticOutput().getVerdict().name()
                        : "UNKNOWN";

                query.addStageTrace("AI_ANALYSIS", StageStatus.SUCCESS, aiDuration,
                        String.format("Retrieved %d passages, extracted %d claims, critic verdict: %s", passageCount, claimCount, criticVerdict));

            } catch (AiServiceUnavailableException | AiServiceTimeoutException | AiServiceException e) {
                long aiDuration = System.currentTimeMillis() - tAi;
                query.setStatus(QueryStatus.FAILED);
                query.setExplanation("AI Service failure: " + e.getMessage());
                query.setCompletedAt(Instant.now());
                query.setTotalDurationMs(System.currentTimeMillis() - startTime);
                query.addStageTrace("AI_ANALYSIS", StageStatus.FAILED, aiDuration, e.getMessage());
                queryRepository.save(query);
                throw e;
            }

            // 3. Apply Deterministic Resolution Engine
            long tRes = System.currentTimeMillis();
            ResolutionRequest resReq = new ResolutionRequest(
                    request.getQuestion().trim(),
                    aiResponse.getSynthesizerOutput(),
                    aiResponse.getCriticOutput(),
                    aiResponse.getPassages()
            );

            ResolutionResponse resResponse = resolutionService.resolve(resReq);
            long resDuration = System.currentTimeMillis() - tRes;

            query.addStageTrace("RESOLUTION_ARBITRATION", StageStatus.SUCCESS, resDuration,
                    String.format("Resolution Engine outcome: %s (%s)", resResponse.getStatus(), resResponse.getExplanation()));

            // 4. Update Query Entity & Audit Status
            QueryStatus queryStatus = (resResponse.getStatus() == ResolutionStatus.CONFLICT || resResponse.getStatus() == ResolutionStatus.INSUFFICIENT_EVIDENCE)
                    ? QueryStatus.ABSTAINED
                    : QueryStatus.COMPLETED;

            query.setStatus(queryStatus);
            query.setResolutionStatus(resResponse.getStatus());
            query.setFinalAnswer(resResponse.getFinalAnswer());
            query.setExplanation(resResponse.getExplanation());
            query.setCompletedAt(Instant.now());
            query.setTotalDurationMs(System.currentTimeMillis() - startTime);

            query.addStageTrace("RESPONSE_ASSEMBLY", StageStatus.SUCCESS, 1L, "Response assembled with provenance and audit metadata");
            QueryEntity saved = queryRepository.save(query);

            log.info("[{}] QA orchestration completed in {}ms: queryId={}, status={}, resolutionStatus={}",
                    correlationId, saved.getTotalDurationMs(), saved.getId(), saved.getStatus(), saved.getResolutionStatus());

            // 5. Construct Response DTO
            QuestionQueryResponse response = new QuestionQueryResponse();
            response.setQueryId(saved.getId());
            response.setCorrelationId(saved.getCorrelationId());
            response.setQuestion(saved.getQuestion());
            response.setStatus(saved.getStatus());
            response.setResolutionStatus(saved.getResolutionStatus());
            response.setFinalAnswer(saved.getFinalAnswer());
            response.setExplanation(saved.getExplanation());
            response.setEvidenceReferences(resResponse.getEvidenceReferences());
            response.setConflictingPositions(resResponse.getConflictingPositions());
            response.setRejectedClaims(resResponse.getRejectedClaims());
            response.setUncertaintyNotes(resResponse.getUncertaintyNotes());
            response.setTotalDurationMs(saved.getTotalDurationMs());
            response.setCreatedAt(saved.getCreatedAt());
            response.setCompletedAt(saved.getCompletedAt());
            response.setMetadata(resResponse.getResolutionMetadata());

            response.setStageTraces(saved.getStageTraces().stream()
                    .map(this::toStageTraceDto)
                    .collect(Collectors.toList()));

            return response;

        } finally {
            MDC.remove("correlationId");
        }
    }

    @Transactional(readOnly = true)
    public QuestionQueryResponse getQuery(UUID queryId) {
        QueryEntity query = queryRepository.findByIdWithTraces(queryId)
                .orElseThrow(() -> new QueryNotFoundException(queryId));

        QuestionQueryResponse response = new QuestionQueryResponse();
        response.setQueryId(query.getId());
        response.setCorrelationId(query.getCorrelationId());
        response.setQuestion(query.getQuestion());
        response.setStatus(query.getStatus());
        response.setResolutionStatus(query.getResolutionStatus());
        response.setFinalAnswer(query.getFinalAnswer());
        response.setExplanation(query.getExplanation());
        response.setTotalDurationMs(query.getTotalDurationMs());
        response.setCreatedAt(query.getCreatedAt());
        response.setCompletedAt(query.getCompletedAt());
        response.setStageTraces(query.getStageTraces().stream()
                .map(this::toStageTraceDto)
                .collect(Collectors.toList()));
        return response;
    }

    @Transactional(readOnly = true)
    public List<QuestionQueryResponse> listQueries() {
        return queryRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(q -> {
                    QuestionQueryResponse res = new QuestionQueryResponse();
                    res.setQueryId(q.getId());
                    res.setCorrelationId(q.getCorrelationId());
                    res.setQuestion(q.getQuestion());
                    res.setStatus(q.getStatus());
                    res.setResolutionStatus(q.getResolutionStatus());
                    res.setFinalAnswer(q.getFinalAnswer());
                    res.setExplanation(q.getExplanation());
                    res.setTotalDurationMs(q.getTotalDurationMs());
                    res.setCreatedAt(q.getCreatedAt());
                    res.setCompletedAt(q.getCompletedAt());
                    return res;
                })
                .collect(Collectors.toList());
    }

    private QueryStageTraceDto toStageTraceDto(QueryStageTraceEntity entity) {
        return new QueryStageTraceDto(
                entity.getId(),
                entity.getStageName(),
                entity.getStatus(),
                entity.getDurationMs(),
                entity.getDetails(),
                entity.getCreatedAt()
        );
    }
}
