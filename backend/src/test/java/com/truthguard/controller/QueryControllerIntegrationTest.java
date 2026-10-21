package com.truthguard.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.truthguard.dto.pipeline.AiPipelineResponse;
import com.truthguard.dto.query.QuestionQueryRequest;
import com.truthguard.dto.resolution.*;
import com.truthguard.exception.AiServiceTimeoutException;
import com.truthguard.exception.AiServiceUnavailableException;
import com.truthguard.model.AuthorityLevel;
import com.truthguard.model.QueryEntity;
import com.truthguard.model.QueryStatus;
import com.truthguard.repository.QueryRepository;
import com.truthguard.service.AgentPipelineClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class QueryControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private QueryRepository queryRepository;

    @MockBean
    private AgentPipelineClient agentPipelineClient;

    @BeforeEach
    void setUp() {
        queryRepository.deleteAll();
    }

    @Test
    @DisplayName("POST /api/v1/queries - Successful ANSWERED query execution")
    void testAskQuestionSuccessAnswered() throws Exception {
        UUID chunkId = UUID.randomUUID();
        RetrievedPassageDto passage = new RetrievedPassageDto();
        passage.setChunkId(chunkId);
        passage.setDocumentId(UUID.randomUUID());
        passage.setVersionId(UUID.randomUUID());
        passage.setVersionIdentifier("v1.0");
        passage.setText("Production database requires TLS 1.3 encryption.");
        passage.setSourceTitle("Security Policy");
        passage.setAuthorityLevel(AuthorityLevel.OFFICIAL.name());
        passage.setEffectiveDate("2025-01-01");

        AtomicClaimDto claim = new AtomicClaimDto("c1", "Database requires TLS 1.3.", ClaimType.FACT, List.of(chunkId));
        claim.setGrounded(true);

        SynthesizerOutputDto synth = new SynthesizerOutputDto();
        synth.setCandidateAnswer("The database requires TLS 1.3 encryption.");
        synth.setClaims(List.of(claim));
        synth.setEvidenceChunkIds(List.of(chunkId));

        CriticOutputDto critic = new CriticOutputDto();
        critic.setVerdict(CriticVerdict.PASS);
        critic.setExplanation("Consistent evidence.");

        AiPipelineResponse aiRes = new AiPipelineResponse();
        aiRes.setQuestion("What encryption is required?");
        aiRes.setPassages(List.of(passage));
        aiRes.setSynthesizerOutput(synth);
        aiRes.setCriticOutput(critic);

        when(agentPipelineClient.executePipeline(any())).thenReturn(aiRes);

        QuestionQueryRequest req = new QuestionQueryRequest("What encryption is required?");
        req.setCorrelationId("test-corr-success");

        mockMvc.perform(post("/api/v1/queries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.resolution_status").value("ANSWERED"))
                .andExpect(jsonPath("$.final_answer").value("The database requires TLS 1.3 encryption."))
                .andExpect(jsonPath("$.evidence_references[0].chunk_id").value(chunkId.toString()))
                .andExpect(jsonPath("$.stage_traces").isArray());

        List<QueryEntity> saved = queryRepository.findAll();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getStatus()).isEqualTo(QueryStatus.COMPLETED);
        assertThat(saved.get(0).getStageTraces()).isNotEmpty();
    }

    @Test
    @DisplayName("POST /api/v1/queries - Unresolvable CONFLICT yields ABSTAINED status with null finalAnswer")
    void testAskQuestionConflictAbstained() throws Exception {
        UUID chunk1 = UUID.randomUUID();
        UUID chunk2 = UUID.randomUUID();

        RetrievedPassageDto p1 = new RetrievedPassageDto();
        p1.setChunkId(chunk1);
        p1.setVersionId(UUID.randomUUID());
        p1.setText("Web session timeout is 30m.");
        p1.setSourceTitle("SecOps Standard");
        p1.setAuthorityLevel(AuthorityLevel.OFFICIAL.name());

        RetrievedPassageDto p2 = new RetrievedPassageDto();
        p2.setChunkId(chunk2);
        p2.setVersionId(UUID.randomUUID());
        p2.setText("Web session timeout is 60m.");
        p2.setSourceTitle("Corporate Standard");
        p2.setAuthorityLevel(AuthorityLevel.OFFICIAL.name());

        SynthesizerOutputDto synth = new SynthesizerOutputDto();
        synth.setCandidateAnswer("Session timeout is 30m.");
        synth.setClaims(List.of(new AtomicClaimDto("c1", "Timeout is 30m", ClaimType.FACT, List.of(chunk1))));

        ContradictionFindingDto contradiction = new ContradictionFindingDto("Session Timeout", true, "30m vs 60m");
        contradiction.setConflictingEvidenceIds(List.of(chunk1, chunk2));
        contradiction.setPositions(List.of(
                new SourcePositionDto(chunk1, "SecOps", "v1", "30m", "30 minutes"),
                new SourcePositionDto(chunk2, "Corporate", "v2", "60m", "60 minutes")
        ));

        CriticOutputDto critic = new CriticOutputDto();
        critic.setVerdict(CriticVerdict.CONFLICT);
        critic.setContradictions(List.of(contradiction));

        AiPipelineResponse aiRes = new AiPipelineResponse();
        aiRes.setQuestion("Session timeout?");
        aiRes.setPassages(List.of(p1, p2));
        aiRes.setSynthesizerOutput(synth);
        aiRes.setCriticOutput(critic);

        when(agentPipelineClient.executePipeline(any())).thenReturn(aiRes);

        mockMvc.perform(post("/api/v1/queries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new QuestionQueryRequest("Session timeout?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ABSTAINED"))
                .andExpect(jsonPath("$.resolution_status").value("CONFLICT"))
                .andExpect(jsonPath("$.final_answer").doesNotExist())
                .andExpect(jsonPath("$.conflicting_positions").isArray());

        List<QueryEntity> saved = queryRepository.findAll();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getStatus()).isEqualTo(QueryStatus.ABSTAINED);
    }

    @Test
    @DisplayName("POST /api/v1/queries - AI service unavailable returns 503 and records FAILED status without false answer")
    void testAskQuestionAiServiceUnavailableReturns503() throws Exception {
        when(agentPipelineClient.executePipeline(any()))
                .thenThrow(new AiServiceUnavailableException("Connection refused to AI service at http://localhost:8000"));

        mockMvc.perform(post("/api/v1/queries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new QuestionQueryRequest("What is the policy?"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("AiServiceUnavailable"));

        List<QueryEntity> saved = queryRepository.findAll();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getStatus()).isEqualTo(QueryStatus.FAILED);
    }

    @Test
    @DisplayName("POST /api/v1/queries - AI service timeout returns 504 and records FAILED status")
    void testAskQuestionAiServiceTimeoutReturns504() throws Exception {
        when(agentPipelineClient.executePipeline(any()))
                .thenThrow(new AiServiceTimeoutException("AI Service LLM execution timed out after 30s"));

        mockMvc.perform(post("/api/v1/queries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new QuestionQueryRequest("Heavy query?"))))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.error").value("AiServiceTimeout"));

        List<QueryEntity> saved = queryRepository.findAll();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getStatus()).isEqualTo(QueryStatus.FAILED);
    }

    @Test
    @DisplayName("POST /api/v1/queries - Empty question yields 400 Bad Request")
    void testAskQuestionEmptyQuestionReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/queries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/queries/{queryId} - Retrieves saved query with trace")
    void testGetQueryById() throws Exception {
        UUID queryId = UUID.randomUUID();
        QueryEntity q = new QueryEntity(queryId, "corr-test", "Sample query", QueryStatus.COMPLETED, Instant.now());
        q.setFinalAnswer("Sample answer");
        q.addStageTrace("AI_ANALYSIS", com.truthguard.model.StageStatus.SUCCESS, 120L, "Traced");
        queryRepository.save(q);

        mockMvc.perform(get("/api/v1/queries/" + queryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query_id").value(queryId.toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.stage_traces[0].stage_name").value("AI_ANALYSIS"));
    }
}
