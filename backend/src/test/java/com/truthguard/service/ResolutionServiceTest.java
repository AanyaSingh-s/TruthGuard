package com.truthguard.service;

import com.truthguard.dto.resolution.*;
import com.truthguard.model.AuthorityLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResolutionServiceTest {

    private ResolutionService resolutionService;

    @BeforeEach
    void setUp() {
        resolutionService = new ResolutionService();
    }

    @Test
    @DisplayName("ANSWERED: Valid grounded claims with consistent official evidence and PASS verdict")
    void testAnsweredWhenEvidenceConsistentAndClaimsGrounded() {
        UUID docId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID chunkId = UUID.randomUUID();

        RetrievedPassageDto passage = new RetrievedPassageDto();
        passage.setChunkId(chunkId);
        passage.setDocumentId(docId);
        passage.setVersionId(versionId);
        passage.setVersionIdentifier("v1.0");
        passage.setText("Production databases must enforce TLS 1.3 encryption for all client connections.");
        passage.setSourceTitle("Data Security Policy");
        passage.setAuthorityLevel(AuthorityLevel.OFFICIAL.name());
        passage.setEffectiveDate("2025-01-01");
        passage.setUploadTimestamp("2025-01-02T10:00:00Z");

        AtomicClaimDto claim = new AtomicClaimDto(
                "c1",
                "Production databases require TLS 1.3 encryption.",
                ClaimType.FACT,
                List.of(chunkId)
        );
        claim.setGrounded(true);

        SynthesizerOutputDto synth = new SynthesizerOutputDto();
        synth.setCandidateAnswer("According to the Data Security Policy, production databases must enforce TLS 1.3 encryption.");
        synth.setClaims(List.of(claim));
        synth.setEvidenceChunkIds(List.of(chunkId));

        CriticOutputDto critic = new CriticOutputDto();
        critic.setVerdict(CriticVerdict.PASS);
        critic.setExplanation("All claims fully substantiated with no contradictions.");

        ResolutionRequest request = new ResolutionRequest(
                "What encryption is required for production databases?",
                synth,
                critic,
                List.of(passage)
        );

        ResolutionResponse response = resolutionService.resolve(request);

        assertThat(response.getStatus()).isEqualTo(ResolutionStatus.ANSWERED);
        assertThat(response.getFinalAnswer()).contains("TLS 1.3");
        assertThat(response.getRejectedClaims()).isEmpty();
        assertThat(response.getEvidenceReferences()).hasSize(1);
        assertThat(response.getEvidenceReferences().get(0).getChunkId()).isEqualTo(chunkId);
        assertThat(response.getConflictingPositions()).isEmpty();
    }

    @Test
    @DisplayName("ANSWERED_WITH_QUALIFICATION: Explicit supersession resolves older version")
    void testAnsweredWithQualificationWhenOlderVersionExplicitlySuperseded() {
        UUID docId = UUID.randomUUID();
        UUID v1Id = UUID.randomUUID();
        UUID v2Id = UUID.randomUUID();
        UUID chunk1 = UUID.randomUUID();
        UUID chunk2 = UUID.randomUUID();

        // Older version 1.0 (session timeout 30m)
        RetrievedPassageDto p1 = new RetrievedPassageDto();
        p1.setChunkId(chunk1);
        p1.setDocumentId(docId);
        p1.setVersionId(v1Id);
        p1.setVersionIdentifier("v1.0");
        p1.setText("Web session idle timeout is 30 minutes.");
        p1.setSourceTitle("Access Control Policy v1.0");
        p1.setAuthorityLevel(AuthorityLevel.OFFICIAL.name());
        p1.setEffectiveDate("2024-01-01");
        p1.setUploadTimestamp("2024-01-02T00:00:00Z");
        p1.setSupersededById(v2Id);

        // Newer version 2.0 (session timeout 60m, explicitly supersedes v1)
        RetrievedPassageDto p2 = new RetrievedPassageDto();
        p2.setChunkId(chunk2);
        p2.setDocumentId(docId);
        p2.setVersionId(v2Id);
        p2.setVersionIdentifier("v2.0");
        p2.setText("Web session idle timeout is 60 minutes.");
        p2.setSourceTitle("Access Control Policy v2.0");
        p2.setAuthorityLevel(AuthorityLevel.OFFICIAL.name());
        p2.setEffectiveDate("2025-01-01");
        p2.setUploadTimestamp("2025-01-02T00:00:00Z");
        p2.setSupersedesVersionId(v1Id);

        AtomicClaimDto claim = new AtomicClaimDto(
                "c1",
                "Web session idle timeout is 60 minutes under the latest policy.",
                ClaimType.FACT,
                List.of(chunk2)
        );
        claim.setGrounded(true);

        SynthesizerOutputDto synth = new SynthesizerOutputDto();
        synth.setCandidateAnswer("The web session idle timeout is 60 minutes.");
        synth.setClaims(List.of(claim));
        synth.setEvidenceChunkIds(List.of(chunk2));

        // Critic flagged contradiction between chunk1 and chunk2, but noted explicit supersession
        ContradictionFindingDto contradiction = new ContradictionFindingDto(
                "Session Timeout",
                true,
                "v1.0 states 30 minutes while v2.0 states 60 minutes"
        );
        contradiction.setConflictingEvidenceIds(List.of(chunk2, chunk1));

        StalenessFindingDto staleness = new StalenessFindingDto(
                v1Id,
                v2Id,
                "v1.0",
                "v2.0",
                true,
                "v2.0 explicitly supersedes v1.0"
        );

        CriticOutputDto critic = new CriticOutputDto();
        critic.setVerdict(CriticVerdict.CONFLICT);
        critic.setContradictions(List.of(contradiction));
        critic.setStalenessFindings(List.of(staleness));

        ResolutionRequest request = new ResolutionRequest(
                "What is the session timeout?",
                synth,
                critic,
                List.of(p1, p2)
        );

        ResolutionResponse response = resolutionService.resolve(request);

        assertThat(response.getStatus()).isEqualTo(ResolutionStatus.ANSWERED_WITH_QUALIFICATION);
        assertThat(response.getFinalAnswer()).contains("60 minutes");
        assertThat(response.getFinalAnswer()).contains("TruthGuard Qualifications");
        assertThat(response.getFinalAnswer()).contains("explicit supersession");
        assertThat(response.getConflictingPositions()).isEmpty();
    }

    @Test
    @DisplayName("CONFLICT: Equally authoritative, equally current policies contradict without supersession")
    void testConflictWhenEquallyAuthoritativePoliciesContradict() {
        UUID docId1 = UUID.randomUUID();
        UUID docId2 = UUID.randomUUID();
        UUID chunk1 = UUID.randomUUID();
        UUID chunk2 = UUID.randomUUID();

        // Policy A: 30 minutes
        RetrievedPassageDto p1 = new RetrievedPassageDto();
        p1.setChunkId(chunk1);
        p1.setDocumentId(docId1);
        p1.setVersionId(UUID.randomUUID());
        p1.setVersionIdentifier("SecOps-2025");
        p1.setText("Web session timeout is strictly 30 minutes.");
        p1.setSourceTitle("SecOps Standard");
        p1.setAuthorityLevel(AuthorityLevel.OFFICIAL.name());
        p1.setEffectiveDate("2025-01-01");
        p1.setUploadTimestamp("2025-01-05T00:00:00Z");

        // Policy B: 60 minutes
        RetrievedPassageDto p2 = new RetrievedPassageDto();
        p2.setChunkId(chunk2);
        p2.setDocumentId(docId2);
        p2.setVersionId(UUID.randomUUID());
        p2.setVersionIdentifier("CorpGov-2025");
        p2.setText("Web session timeout is strictly 60 minutes.");
        p2.setSourceTitle("Corporate IT Standard");
        p2.setAuthorityLevel(AuthorityLevel.OFFICIAL.name());
        p2.setEffectiveDate("2025-01-01");
        p2.setUploadTimestamp("2025-01-05T00:00:00Z");

        // Synthesizer produced a blended or arbitrary 45m or 30m answer
        SynthesizerOutputDto synth = new SynthesizerOutputDto();
        synth.setCandidateAnswer("The session timeout is 30 minutes.");
        synth.setClaims(List.of(
                new AtomicClaimDto("c1", "Session timeout is 30 minutes.", ClaimType.FACT, List.of(chunk1))
        ));

        // Critic identifies genuine conflict on same scope
        ContradictionFindingDto contradiction = new ContradictionFindingDto(
                "Web Session Timeout",
                true,
                "SecOps requires 30m while Corporate IT requires 60m"
        );
        contradiction.setConflictingEvidenceIds(List.of(chunk1, chunk2));
        contradiction.setPositions(List.of(
                new SourcePositionDto(chunk1, "SecOps Standard", "SecOps-2025", "strictly 30 minutes", "30 min timeout"),
                new SourcePositionDto(chunk2, "Corporate IT Standard", "CorpGov-2025", "strictly 60 minutes", "60 min timeout")
        ));

        CriticOutputDto critic = new CriticOutputDto();
        critic.setVerdict(CriticVerdict.CONFLICT);
        critic.setContradictions(List.of(contradiction));

        ResolutionRequest request = new ResolutionRequest(
                "What is the web session timeout?",
                synth,
                critic,
                List.of(p1, p2)
        );

        ResolutionResponse response = resolutionService.resolve(request);

        // TruthGuard preserves the conflict and refuses to guess or average to 45m!
        assertThat(response.getStatus()).isEqualTo(ResolutionStatus.CONFLICT);
        assertThat(response.getFinalAnswer()).isNull();
        assertThat(response.getConflictingPositions()).hasSize(2);
        assertThat(response.getExplanation()).contains("Unresolvable contradiction between equally authoritative, equally current sources");
        assertThat(response.getExplanation()).contains("abstains from averaging");
    }

    @Test
    @DisplayName("CONFLICT: Simultaneous updates on same effective date without supersession")
    void testConflictOnSimultaneousUpdatesWithoutSupersession() {
        UUID chunkA = UUID.randomUUID();
        UUID chunkB = UUID.randomUUID();

        RetrievedPassageDto p1 = new RetrievedPassageDto();
        p1.setChunkId(chunkA);
        p1.setVersionId(UUID.randomUUID());
        p1.setVersionIdentifier("Rev-A");
        p1.setText("Allowed PTO carryover is 5 days.");
        p1.setSourceTitle("HR Handbook Rev A");
        p1.setAuthorityLevel(AuthorityLevel.OFFICIAL.name());
        p1.setEffectiveDate("2025-06-01");

        RetrievedPassageDto p2 = new RetrievedPassageDto();
        p2.setChunkId(chunkB);
        p2.setVersionId(UUID.randomUUID());
        p2.setVersionIdentifier("Rev-B");
        p2.setText("Allowed PTO carryover is 10 days.");
        p2.setSourceTitle("HR Handbook Rev B");
        p2.setAuthorityLevel(AuthorityLevel.OFFICIAL.name());
        p2.setEffectiveDate("2025-06-01");

        SynthesizerOutputDto synth = new SynthesizerOutputDto();
        synth.setCandidateAnswer("PTO carryover is 5 days.");
        synth.setClaims(List.of(new AtomicClaimDto("c1", "PTO carryover is 5 days", ClaimType.FACT, List.of(chunkA))));

        ContradictionFindingDto contradiction = new ContradictionFindingDto("PTO Carryover", true, "5 days vs 10 days");
        contradiction.setConflictingEvidenceIds(List.of(chunkA, chunkB));

        CriticOutputDto critic = new CriticOutputDto();
        critic.setVerdict(CriticVerdict.CONFLICT);
        critic.setContradictions(List.of(contradiction));

        ResolutionResponse response = resolutionService.resolve(new ResolutionRequest("PTO carryover", synth, critic, List.of(p1, p2)));

        assertThat(response.getStatus()).isEqualTo(ResolutionStatus.CONFLICT);
        assertThat(response.getFinalAnswer()).isNull();
    }

    @Test
    @DisplayName("RULE: Upload timestamp does NOT override effective policy date")
    void testUploadTimestampDoesNotOverrideEffectivePolicyDate() {
        UUID docId = UUID.randomUUID();
        UUID v1Id = UUID.randomUUID(); // Newer policy
        UUID v2Id = UUID.randomUUID(); // Backfilled archive policy
        UUID chunkNew = UUID.randomUUID();
        UUID chunkOld = UUID.randomUUID();

        // Modern policy effective 2026, uploaded in 2026
        RetrievedPassageDto modern = new RetrievedPassageDto();
        modern.setChunkId(chunkNew);
        modern.setDocumentId(docId);
        modern.setVersionId(v1Id);
        modern.setVersionIdentifier("v2026");
        modern.setText("Minimum password length is 16 characters.");
        modern.setSourceTitle("Password Policy");
        modern.setAuthorityLevel(AuthorityLevel.OFFICIAL.name());
        modern.setEffectiveDate("2026-01-01");
        modern.setUploadTimestamp("2026-01-01T12:00:00Z");

        // Legacy 2018 policy re-uploaded today during data migration / backfill
        RetrievedPassageDto backfill = new RetrievedPassageDto();
        backfill.setChunkId(chunkOld);
        backfill.setDocumentId(docId);
        backfill.setVersionId(v2Id);
        backfill.setVersionIdentifier("v2018-Archive");
        backfill.setText("Minimum password length is 8 characters.");
        backfill.setSourceTitle("Password Policy Legacy Archive");
        backfill.setAuthorityLevel(AuthorityLevel.OFFICIAL.name());
        backfill.setEffectiveDate("2018-01-01");
        backfill.setUploadTimestamp("2026-10-21T09:00:00Z"); // Uploaded today!

        SynthesizerOutputDto synth = new SynthesizerOutputDto();
        synth.setCandidateAnswer("Password length is 16 characters.");
        synth.setClaims(List.of(new AtomicClaimDto("c1", "Password length is 16 characters", ClaimType.FACT, List.of(chunkNew))));

        // Notice: backfill has a later upload timestamp than modern, but neither supersedes the other.
        // The engine must NOT declare backfill the winner simply because upload timestamp is later!
        ContradictionFindingDto contradiction = new ContradictionFindingDto("Password Length", true, "16 chars vs 8 chars");
        contradiction.setConflictingEvidenceIds(List.of(chunkNew, chunkOld));

        CriticOutputDto critic = new CriticOutputDto();
        critic.setVerdict(CriticVerdict.CONFLICT);
        critic.setContradictions(List.of(contradiction));

        ResolutionResponse response = resolutionService.resolve(new ResolutionRequest("Password length", synth, critic, List.of(modern, backfill)));

        // Because backfill has an older effective date ("2018-01-01") and no supersession link,
        // it cannot supersede modern despite its recent upload timestamp.
        // It is preserved as a CONFLICT rather than erroneously accepting backfill as the newest policy!
        assertThat(response.getStatus()).isEqualTo(ResolutionStatus.CONFLICT);
        assertThat(response.getFinalAnswer()).isNull();
    }

    @Test
    @DisplayName("ANSWERED_WITH_QUALIFICATION: Differentiated scopes do not trigger conflict")
    void testScopeDifferentiatedClaimsDoNotTriggerConflict() {
        UUID chunkUS = UUID.randomUUID();
        UUID chunkUK = UUID.randomUUID();

        RetrievedPassageDto pUS = new RetrievedPassageDto();
        pUS.setChunkId(chunkUS);
        pUS.setVersionId(UUID.randomUUID());
        pUS.setVersionIdentifier("US-Policy");
        pUS.setText("US 401(k) retirement match is 5%.");
        pUS.setSourceTitle("US Benefits");
        pUS.setAuthorityLevel(AuthorityLevel.OFFICIAL.name());

        RetrievedPassageDto pUK = new RetrievedPassageDto();
        pUK.setChunkId(chunkUK);
        pUK.setVersionId(UUID.randomUUID());
        pUK.setVersionIdentifier("UK-Policy");
        pUK.setText("UK pension employer contribution is 8%.");
        pUK.setSourceTitle("UK Benefits");
        pUK.setAuthorityLevel(AuthorityLevel.OFFICIAL.name());

        SynthesizerOutputDto synth = new SynthesizerOutputDto();
        synth.setCandidateAnswer("Retirement benefits vary: 5% 401(k) in the US and 8% pension in the UK.");
        synth.setClaims(List.of(
                new AtomicClaimDto("c1", "US match is 5%", ClaimType.FACT, List.of(chunkUS)),
                new AtomicClaimDto("c2", "UK match is 8%", ClaimType.FACT, List.of(chunkUK))
        ));

        // Critic notes different percentages, but scopeAligned = false (different jurisdictions)
        ContradictionFindingDto diffScope = new ContradictionFindingDto(
                "Retirement Match",
                false, // scope is NOT aligned!
                "Applies to different jurisdictions: US vs UK"
        );
        diffScope.setConflictingEvidenceIds(List.of(chunkUS, chunkUK));

        CriticOutputDto critic = new CriticOutputDto();
        critic.setVerdict(CriticVerdict.PASS);
        critic.setContradictions(List.of(diffScope));

        ResolutionResponse response = resolutionService.resolve(new ResolutionRequest("What is the retirement match?", synth, critic, List.of(pUS, pUK)));

        assertThat(response.getStatus()).isEqualTo(ResolutionStatus.ANSWERED_WITH_QUALIFICATION);
        assertThat(response.getFinalAnswer()).contains("5% 401(k)");
        assertThat(response.getFinalAnswer()).contains("8% pension");
        assertThat(response.getFinalAnswer()).contains("differentiated scopes");
        assertThat(response.getConflictingPositions()).isEmpty();
    }

    @Test
    @DisplayName("ANSWERED_WITH_QUALIFICATION: Authority precedence resolves OFFICIAL over DRAFT")
    void testAuthorityPrecedenceResolvesDraftVsOfficial() {
        UUID chunkDraft = UUID.randomUUID();
        UUID chunkOfficial = UUID.randomUUID();

        RetrievedPassageDto draftPassage = new RetrievedPassageDto();
        draftPassage.setChunkId(chunkDraft);
        draftPassage.setVersionId(UUID.randomUUID());
        draftPassage.setVersionIdentifier("Draft-Proposal");
        draftPassage.setText("Remote work is permitted 5 days a week.");
        draftPassage.setSourceTitle("Workplace Policy Draft");
        draftPassage.setAuthorityLevel(AuthorityLevel.DRAFT.name()); // rank 1

        RetrievedPassageDto officialPassage = new RetrievedPassageDto();
        officialPassage.setChunkId(chunkOfficial);
        officialPassage.setVersionId(UUID.randomUUID());
        officialPassage.setVersionIdentifier("Official-Handbook");
        officialPassage.setText("Remote work is permitted 2 days a week.");
        officialPassage.setSourceTitle("Employee Handbook");
        officialPassage.setAuthorityLevel(AuthorityLevel.OFFICIAL.name()); // rank 3

        SynthesizerOutputDto synth = new SynthesizerOutputDto();
        synth.setCandidateAnswer("Remote work is permitted 2 days per week.");
        synth.setClaims(List.of(
                new AtomicClaimDto("c1", "Remote work is 2 days a week", ClaimType.FACT, List.of(chunkOfficial))
        ));

        ContradictionFindingDto contradiction = new ContradictionFindingDto("Remote Work Days", true, "5 days vs 2 days");
        contradiction.setConflictingEvidenceIds(List.of(chunkDraft, chunkOfficial));

        CriticOutputDto critic = new CriticOutputDto();
        critic.setVerdict(CriticVerdict.CONFLICT);
        critic.setContradictions(List.of(contradiction));

        ResolutionResponse response = resolutionService.resolve(new ResolutionRequest("Remote work policy", synth, critic, List.of(draftPassage, officialPassage)));

        // OFFICIAL (rank 3) takes deterministic precedence over DRAFT (rank 1)
        assertThat(response.getStatus()).isEqualTo(ResolutionStatus.ANSWERED_WITH_QUALIFICATION);
        assertThat(response.getFinalAnswer()).contains("2 days");
        assertThat(response.getFinalAnswer()).contains("authority precedence");
        assertThat(response.getConflictingPositions()).isEmpty();
    }

    @Test
    @DisplayName("ANSWERED_WITH_QUALIFICATION: Hallucinated / invalid chunk ID claims are rejected while valid claims remain")
    void testUnsupportedClaimsIdentifiedAndRejected() {
        UUID validChunk = UUID.randomUUID();
        UUID hallucinatedChunk = UUID.randomUUID(); // Not present in passages!

        RetrievedPassageDto passage = new RetrievedPassageDto();
        passage.setChunkId(validChunk);
        passage.setVersionId(UUID.randomUUID());
        passage.setVersionIdentifier("v1.0");
        passage.setText("Primary data center is located in Oregon.");
        passage.setSourceTitle("Infrastructure Spec");
        passage.setAuthorityLevel(AuthorityLevel.OFFICIAL.name());

        AtomicClaimDto validClaim = new AtomicClaimDto("c1", "Primary data center is in Oregon.", ClaimType.FACT, List.of(validChunk));
        validClaim.setGrounded(true);

        AtomicClaimDto fakeClaim = new AtomicClaimDto("c2", "Secondary data center is in Tokyo.", ClaimType.FACT, List.of(hallucinatedChunk));

        SynthesizerOutputDto synth = new SynthesizerOutputDto();
        synth.setCandidateAnswer("Primary data center is in Oregon, secondary in Tokyo.");
        synth.setClaims(List.of(validClaim, fakeClaim));
        synth.setEvidenceChunkIds(List.of(validChunk));

        CriticOutputDto critic = new CriticOutputDto();
        critic.setVerdict(CriticVerdict.UNSUPPORTED);
        critic.setUnsupportedClaimIds(List.of("c2"));

        ResolutionResponse response = resolutionService.resolve(new ResolutionRequest("Data centers?", synth, critic, List.of(passage)));

        assertThat(response.getStatus()).isEqualTo(ResolutionStatus.ANSWERED_WITH_QUALIFICATION);
        assertThat(response.getRejectedClaims()).hasSize(1);
        assertThat(response.getRejectedClaims().get(0).getClaimId()).isEqualTo("c2");
        assertThat(response.getRejectedClaims().get(0).getRejectionReason()).contains("Hallucinated or unverified");
        assertThat(response.getEvidenceReferences()).hasSize(1);
        assertThat(response.getEvidenceReferences().get(0).getChunkId()).isEqualTo(validChunk);
    }

    @Test
    @DisplayName("INSUFFICIENT_EVIDENCE: Passages list is empty")
    void testInsufficientEvidenceWhenPassagesEmpty() {
        SynthesizerOutputDto synth = new SynthesizerOutputDto();
        synth.setCandidateAnswer("Something fabricated.");

        CriticOutputDto critic = new CriticOutputDto();
        critic.setVerdict(CriticVerdict.INSUFFICIENT_EVIDENCE);

        ResolutionResponse response = resolutionService.resolve(new ResolutionRequest("Query", synth, critic, List.of()));

        assertThat(response.getStatus()).isEqualTo(ResolutionStatus.INSUFFICIENT_EVIDENCE);
        assertThat(response.getFinalAnswer()).isNull();
        assertThat(response.getExplanation()).contains("Insufficient evidence");
    }

    @Test
    @DisplayName("INSUFFICIENT_EVIDENCE: Critic reports INSUFFICIENT_EVIDENCE verdict")
    void testInsufficientEvidenceWhenCriticFlagsInsufficient() {
        UUID chunkId = UUID.randomUUID();
        RetrievedPassageDto p = new RetrievedPassageDto();
        p.setChunkId(chunkId);
        p.setText("Random irrelevant text about office plants.");

        SynthesizerOutputDto synth = new SynthesizerOutputDto();
        synth.setCandidateAnswer("Unsubstantiated answer.");
        synth.setClaims(List.of(new AtomicClaimDto("c1", "Claim", ClaimType.FACT, List.of(chunkId))));

        CriticOutputDto critic = new CriticOutputDto();
        critic.setVerdict(CriticVerdict.INSUFFICIENT_EVIDENCE);
        critic.setExplanation("Retrieved passage does not contain necessary facts for the question.");

        ResolutionResponse response = resolutionService.resolve(new ResolutionRequest("Query", synth, critic, List.of(p)));

        assertThat(response.getStatus()).isEqualTo(ResolutionStatus.INSUFFICIENT_EVIDENCE);
        assertThat(response.getFinalAnswer()).isNull();
        assertThat(response.getExplanation()).contains("Critic determined evidence is insufficient");
    }

    @Test
    @DisplayName("INSUFFICIENT_EVIDENCE: All claims are ungrounded / cite hallucinated chunk IDs")
    void testInsufficientEvidenceWhenAllClaimsUngrounded() {
        RetrievedPassageDto p = new RetrievedPassageDto();
        p.setChunkId(UUID.randomUUID());
        p.setText("Relevant text.");

        AtomicClaimDto fakeClaim = new AtomicClaimDto("c1", "Fabricated claim", ClaimType.FACT, List.of(UUID.randomUUID()));

        SynthesizerOutputDto synth = new SynthesizerOutputDto();
        synth.setCandidateAnswer("Fabricated answer.");
        synth.setClaims(List.of(fakeClaim));

        CriticOutputDto critic = new CriticOutputDto();
        critic.setVerdict(CriticVerdict.UNSUPPORTED);
        critic.setUnsupportedClaimIds(List.of("c1"));

        ResolutionResponse response = resolutionService.resolve(new ResolutionRequest("Query", synth, critic, List.of(p)));

        assertThat(response.getStatus()).isEqualTo(ResolutionStatus.INSUFFICIENT_EVIDENCE);
        assertThat(response.getFinalAnswer()).isNull();
        assertThat(response.getRejectedClaims()).hasSize(1);
    }

    @Test
    @DisplayName("INSUFFICIENT_EVIDENCE: Missing candidate answer from synthesizer")
    void testMissingCandidateAnswerYieldsInsufficientEvidence() {
        RetrievedPassageDto p = new RetrievedPassageDto();
        p.setChunkId(UUID.randomUUID());
        p.setText("Relevant text.");

        SynthesizerOutputDto synth = new SynthesizerOutputDto();
        synth.setCandidateAnswer("");

        CriticOutputDto critic = new CriticOutputDto();

        ResolutionResponse response = resolutionService.resolve(new ResolutionRequest("Query", synth, critic, List.of(p)));

        assertThat(response.getStatus()).isEqualTo(ResolutionStatus.INSUFFICIENT_EVIDENCE);
        assertThat(response.getFinalAnswer()).isNull();
    }

    @Test
    @DisplayName("Null request throws IllegalArgumentException")
    void testNullRequestThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> resolutionService.resolve(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ResolutionRequest cannot be null");
    }
}
