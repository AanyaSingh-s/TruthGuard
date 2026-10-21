package com.truthguard.dto.query;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.truthguard.dto.resolution.ConflictingPositionDto;
import com.truthguard.dto.resolution.EvidenceReferenceDto;
import com.truthguard.dto.resolution.RejectedClaimDto;
import com.truthguard.dto.resolution.ResolutionStatus;
import com.truthguard.model.QueryStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class QuestionQueryResponse {

    private UUID queryId;
    private String correlationId;
    private String question;
    private QueryStatus status;
    private ResolutionStatus resolutionStatus;
    private String finalAnswer;
    private String explanation;
    private List<EvidenceReferenceDto> evidenceReferences = new ArrayList<>();
    private List<ConflictingPositionDto> conflictingPositions = new ArrayList<>();
    private List<RejectedClaimDto> rejectedClaims = new ArrayList<>();
    private List<String> uncertaintyNotes = new ArrayList<>();
    private List<QueryStageTraceDto> stageTraces = new ArrayList<>();
    private Long totalDurationMs;
    private Instant createdAt;
    private Instant completedAt;
    private Map<String, Object> metadata = new HashMap<>();

    public QuestionQueryResponse() {
    }

    public UUID getQueryId() {
        return queryId;
    }

    public void setQueryId(UUID queryId) {
        this.queryId = queryId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public QueryStatus getStatus() {
        return status;
    }

    public void setStatus(QueryStatus status) {
        this.status = status;
    }

    public ResolutionStatus getResolutionStatus() {
        return resolutionStatus;
    }

    public void setResolutionStatus(ResolutionStatus resolutionStatus) {
        this.resolutionStatus = resolutionStatus;
    }

    public String getFinalAnswer() {
        return finalAnswer;
    }

    public void setFinalAnswer(String finalAnswer) {
        this.finalAnswer = finalAnswer;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public List<EvidenceReferenceDto> getEvidenceReferences() {
        return evidenceReferences;
    }

    public void setEvidenceReferences(List<EvidenceReferenceDto> evidenceReferences) {
        this.evidenceReferences = evidenceReferences != null ? evidenceReferences : new ArrayList<>();
    }

    public List<ConflictingPositionDto> getConflictingPositions() {
        return conflictingPositions;
    }

    public void setConflictingPositions(List<ConflictingPositionDto> conflictingPositions) {
        this.conflictingPositions = conflictingPositions != null ? conflictingPositions : new ArrayList<>();
    }

    public List<RejectedClaimDto> getRejectedClaims() {
        return rejectedClaims;
    }

    public void setRejectedClaims(List<RejectedClaimDto> rejectedClaims) {
        this.rejectedClaims = rejectedClaims != null ? rejectedClaims : new ArrayList<>();
    }

    public List<String> getUncertaintyNotes() {
        return uncertaintyNotes;
    }

    public void setUncertaintyNotes(List<String> uncertaintyNotes) {
        this.uncertaintyNotes = uncertaintyNotes != null ? uncertaintyNotes : new ArrayList<>();
    }

    public List<QueryStageTraceDto> getStageTraces() {
        return stageTraces;
    }

    public void setStageTraces(List<QueryStageTraceDto> stageTraces) {
        this.stageTraces = stageTraces != null ? stageTraces : new ArrayList<>();
    }

    public Long getTotalDurationMs() {
        return totalDurationMs;
    }

    public void setTotalDurationMs(Long totalDurationMs) {
        this.totalDurationMs = totalDurationMs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata != null ? metadata : new HashMap<>();
    }
}
