package com.truthguard.service;

import com.truthguard.dto.resolution.*;
import com.truthguard.model.AuthorityLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ResolutionServiceDeterministicRulesTest (Adversarial Corpus Fixtures).
 * <p>
 * Unit test suite evaluating TruthGuard's deterministic Resolution Engine logic
 * against canonical typed scenario fixtures representing the benchmark corpus cases.
 * These unit tests verify resolution arbitration independently of live AI services.
 */
@DisplayName("ResolutionService Deterministic Arbitration Rule Tests")
class AdversarialCorpusEvaluationTest {

    private ResolutionService resolutionService;

    @BeforeEach
    void setUp() {
        resolutionService = new ResolutionService();
    }

    @Test
    @DisplayName("Scenario 1: Two documents that agree -> ANSWERED with valid citation")
    void testCorpusScenario1Agreement() {
        UUID chunk1 = UUID.randomUUID();
        UUID chunk2 = UUID.randomUUID();

        RetrievedPassageDto p1 = new RetrievedPassageDto();
        p1.setChunkId(chunk1);
        p1.setText("Full-time employees receive 25 days of paid annual leave.");
        p1.setSourceTitle("Benefits Handbook");
        p1.setAuthorityLevel(AuthorityLevel.OFFICIAL.name());

        RetrievedPassageDto p2 = new RetrievedPassageDto();
        p2.setChunkId(chunk2);
        p2.setText("Annual vacation allowance is 25 days.");
        p2.setSourceTitle("HR Vacation Policy");
        p2.setAuthorityLevel(AuthorityLevel.OFFICIAL.name());

        SynthesizerOutputDto synth = new SynthesizerOutputDto();
        synth.setCandidateAnswer("Full-time employees receive 25 days of paid annual leave.");
        synth.setClaims(List.of(new AtomicClaimDto("c1", "Annual leave is 25 days", ClaimType.FACT, List.of(chunk1, chunk2))));

        CriticOutputDto critic = new CriticOutputDto();
        critic.setVerdict(CriticVerdict.PASS);

        ResolutionResponse res = resolutionService.resolve(new ResolutionRequest("Annual leave days?", synth, critic, List.of(p1, p2)));

        assertThat(res.getStatus()).isEqualTo(ResolutionStatus.ANSWERED);
        assertThat(res.getFinalAnswer()).contains("25 days");
        assertThat(res.getEvidenceReferences()).hasSize(2);
    }

    @Test
    @DisplayName("Scenario 2: Contradictory documents with equal authority on same scope -> CONFLICT (no blending to 45m)")
    void testCorpusScenario2ContradictionEqualAuthority() {
        UUID chunk1 = UUID.randomUUID();
        UUID chunk2 = UUID.randomUUID();

        RetrievedPassageDto p1 = new RetrievedPassageDto();
        p1.setChunkId(chunk1);
        p1.setText("Session timeout is strictly 30 minutes.");
        p1.setSourceTitle("SecOps Standard");
        p1.setAuthorityLevel(AuthorityLevel.OFFICIAL.name());
        p1.setEffectiveDate("2025-01-01");

        RetrievedPassageDto p2 = new RetrievedPassageDto();
        p2.setChunkId(chunk2);
        p2.setText("Session timeout is strictly 60 minutes.");
        p2.setSourceTitle("Corporate IT Standard");
        p2.setAuthorityLevel(AuthorityLevel.OFFICIAL.name());
        p2.setEffectiveDate("2025-01-01");

        SynthesizerOutputDto synth = new SynthesizerOutputDto();
        synth.setCandidateAnswer("Session timeout is 30 minutes.");
        synth.setClaims(List.of(new AtomicClaimDto("c1", "Session timeout is 30m", ClaimType.FACT, List.of(chunk1))));

        ContradictionFindingDto contradiction = new ContradictionFindingDto("Session Timeout", true, "30m vs 60m");
        contradiction.setConflictingEvidenceIds(List.of(chunk1, chunk2));

        CriticOutputDto critic = new CriticOutputDto();
        critic.setVerdict(CriticVerdict.CONFLICT);
        critic.setContradictions(List.of(contradiction));

        ResolutionResponse res = resolutionService.resolve(new ResolutionRequest("Session timeout?", synth, critic, List.of(p1, p2)));

        assertThat(res.getStatus()).isEqualTo(ResolutionStatus.CONFLICT);
        assertThat(res.getFinalAnswer()).isNull(); // Abstains! No blending or compromise!
    }

    @Test
    @DisplayName("Scenario 3: Explicit supersession resolves older version -> ANSWERED_WITH_QUALIFICATION")
    void testCorpusScenario3ExplicitSupersession() {
        UUID v1 = UUID.randomUUID();
        UUID v2 = UUID.randomUUID();
        UUID chunk1 = UUID.randomUUID();
        UUID chunk2 = UUID.randomUUID();

        RetrievedPassageDto p1 = new RetrievedPassageDto();
        p1.setChunkId(chunk1);
        p1.setVersionId(v1);
        p1.setText("Customer transaction records must be retained for 3 years.");
        p1.setSourceTitle("Data Retention Policy v1.0");
        p1.setAuthorityLevel(AuthorityLevel.OFFICIAL.name());
        p1.setEffectiveDate("2023-01-01");
        p1.setSupersededById(v2);

        RetrievedPassageDto p2 = new RetrievedPassageDto();
        p2.setChunkId(chunk2);
        p2.setVersionId(v2);
        p2.setText("Customer transaction records must be retained for 7 years.");
        p2.setSourceTitle("Data Retention Policy v2.0");
        p2.setAuthorityLevel(AuthorityLevel.OFFICIAL.name());
        p2.setEffectiveDate("2025-01-01");
        p2.setSupersedesVersionId(v1);

        SynthesizerOutputDto synth = new SynthesizerOutputDto();
        synth.setCandidateAnswer("Customer data retention duration is 7 years.");
        synth.setClaims(List.of(new AtomicClaimDto("c1", "Retention is 7 years", ClaimType.FACT, List.of(chunk2))));

        ContradictionFindingDto contradiction = new ContradictionFindingDto("Data Retention", true, "3 years vs 7 years");
        contradiction.setConflictingEvidenceIds(List.of(chunk1, chunk2));

        StalenessFindingDto staleness = new StalenessFindingDto(v1, v2, "v1.0", "v2.0", true, "v2.0 explicitly supersedes v1.0");

        CriticOutputDto critic = new CriticOutputDto();
        critic.setVerdict(CriticVerdict.CONFLICT);
        critic.setContradictions(List.of(contradiction));
        critic.setStalenessFindings(List.of(staleness));

        ResolutionResponse res = resolutionService.resolve(new ResolutionRequest("Data retention duration?", synth, critic, List.of(p1, p2)));

        assertThat(res.getStatus()).isEqualTo(ResolutionStatus.ANSWERED_WITH_QUALIFICATION);
        assertThat(res.getFinalAnswer()).contains("7 years");
        assertThat(res.getFinalAnswer()).contains("explicit supersession");
    }

    @Test
    @DisplayName("Scenario 4: Newer upload does NOT override older effective policy without supersession")
    void testCorpusScenario4NewerUploadNoSupersession() {
        UUID chunkModern = UUID.randomUUID();
        UUID chunkArchive = UUID.randomUUID();

        RetrievedPassageDto modern = new RetrievedPassageDto();
        modern.setChunkId(chunkModern);
        modern.setText("Minimum password length is 16 characters.");
        modern.setAuthorityLevel(AuthorityLevel.OFFICIAL.name());
        modern.setEffectiveDate("2026-01-01");
        modern.setUploadTimestamp("2026-01-02T00:00:00Z");

        RetrievedPassageDto archive = new RetrievedPassageDto();
        archive.setChunkId(chunkArchive);
        archive.setText("Minimum password length is 8 characters.");
        archive.setAuthorityLevel(AuthorityLevel.OFFICIAL.name());
        archive.setEffectiveDate("2018-01-01");
        archive.setUploadTimestamp("2026-10-21T18:00:00Z"); // Uploaded today!

        SynthesizerOutputDto synth = new SynthesizerOutputDto();
        synth.setCandidateAnswer("Password length is 16 characters.");
        synth.setClaims(List.of(new AtomicClaimDto("c1", "Password length 16", ClaimType.FACT, List.of(chunkModern))));

        ContradictionFindingDto contradiction = new ContradictionFindingDto("Password Length", true, "16 chars vs 8 chars");
        contradiction.setConflictingEvidenceIds(List.of(chunkModern, chunkArchive));

        CriticOutputDto critic = new CriticOutputDto();
        critic.setVerdict(CriticVerdict.CONFLICT);
        critic.setContradictions(List.of(contradiction));

        ResolutionResponse res = resolutionService.resolve(new ResolutionRequest("Password length?", synth, critic, List.of(modern, archive)));

        assertThat(res.getStatus()).isEqualTo(ResolutionStatus.CONFLICT);
        assertThat(res.getFinalAnswer()).isNull();
    }

    @Test
    @DisplayName("Scenario 5: Unsupported claims in candidate answer are detected and rejected")
    void testCorpusScenario5UnsupportedClaims() {
        UUID validChunk = UUID.randomUUID();
        UUID fakeChunk = UUID.randomUUID();

        RetrievedPassageDto p = new RetrievedPassageDto();
        p.setChunkId(validChunk);
        p.setText("Primary engineering hub is located in Seattle.");
        p.setAuthorityLevel(AuthorityLevel.OFFICIAL.name());

        AtomicClaimDto c1 = new AtomicClaimDto("c1", "Primary hub in Seattle.", ClaimType.FACT, List.of(validChunk));
        AtomicClaimDto c2 = new AtomicClaimDto("c2", "Secondary hub in Tokyo.", ClaimType.FACT, List.of(fakeChunk));

        SynthesizerOutputDto synth = new SynthesizerOutputDto();
        synth.setCandidateAnswer("Primary hub in Seattle, secondary in Tokyo.");
        synth.setClaims(List.of(c1, c2));

        CriticOutputDto critic = new CriticOutputDto();
        critic.setVerdict(CriticVerdict.UNSUPPORTED);
        critic.setUnsupportedClaimIds(List.of("c2"));

        ResolutionResponse res = resolutionService.resolve(new ResolutionRequest("Hub locations?", synth, critic, List.of(p)));

        assertThat(res.getStatus()).isEqualTo(ResolutionStatus.ANSWERED_WITH_QUALIFICATION);
        assertThat(res.getRejectedClaims()).hasSize(1);
        assertThat(res.getRejectedClaims().get(0).getClaimId()).isEqualTo("c2");
    }

    @Test
    @DisplayName("Scenario 6: No relevant evidence -> INSUFFICIENT_EVIDENCE")
    void testCorpusScenario6NoRelevantEvidence() {
        SynthesizerOutputDto synth = new SynthesizerOutputDto();
        synth.setCandidateAnswer("Fabricated severance schedule.");

        CriticOutputDto critic = new CriticOutputDto();
        critic.setVerdict(CriticVerdict.INSUFFICIENT_EVIDENCE);

        ResolutionResponse res = resolutionService.resolve(new ResolutionRequest("Severance schedule?", synth, critic, List.of()));

        assertThat(res.getStatus()).isEqualTo(ResolutionStatus.INSUFFICIENT_EVIDENCE);
        assertThat(res.getFinalAnswer()).isNull();
    }
}
