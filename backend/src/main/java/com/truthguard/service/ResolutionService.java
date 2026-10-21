package com.truthguard.service;

import com.truthguard.dto.resolution.*;
import com.truthguard.model.AuthorityLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Deterministic Resolution Engine for TruthGuard.
 * <p>
 * Arbitrates between Synthesizer answers and Critic assessments using deterministic rules:
 * 1. Evidence Existence Evaluation (empty evidence or critic insufficient verdict -> INSUFFICIENT_EVIDENCE)
 * 2. Citation & Grounding Validation (claims citing hallucinated or unverified chunk IDs are rejected)
 * 3. Contradiction & Scope Differentiation (different scopes do not constitute genuine conflicts)
 * 4. Explicit Supersession (newer effective version with explicit supersession link resolves older version)
 * 5. Authority Precedence (higher authority rank resolves lower authority rank on same scope)
 * 6. Conflict Preservation (equally authoritative, equally current sources on same scope -> CONFLICT, abstain)
 */
@Service
public class ResolutionService {

    private static final Logger log = LoggerFactory.getLogger(ResolutionService.class);

    public ResolutionResponse resolve(ResolutionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("ResolutionRequest cannot be null");
        }

        List<RetrievedPassageDto> passages = request.getPassages() != null ? request.getPassages() : Collections.emptyList();
        SynthesizerOutputDto synthesizerOutput = request.getSynthesizerOutput();
        CriticOutputDto criticOutput = request.getCriticOutput();

        Map<String, Object> metadata = new HashMap<>();
        List<String> uncertaintyNotes = new ArrayList<>();
        List<String> qualifications = new ArrayList<>();

        if (synthesizerOutput != null && synthesizerOutput.getUncertaintyNotes() != null) {
            uncertaintyNotes.addAll(synthesizerOutput.getUncertaintyNotes());
        }
        if (criticOutput != null && criticOutput.getUncertaintyNotes() != null) {
            uncertaintyNotes.addAll(criticOutput.getUncertaintyNotes());
        }

        // =========================================================================
        // PHASE 1: Evidence Existence Check
        // =========================================================================
        if (passages.isEmpty()) {
            log.warn("Deterministic Resolution: No passages provided in request.");
            ResolutionResponse response = new ResolutionResponse(
                    ResolutionStatus.INSUFFICIENT_EVIDENCE,
                    null,
                    "Insufficient evidence: no relevant evidence passages were retrieved."
            );
            response.setUncertaintyNotes(uncertaintyNotes);
            metadata.put("failureReason", "EMPTY_PASSAGES");
            response.setResolutionMetadata(metadata);
            return response;
        }

        if (criticOutput != null && criticOutput.getVerdict() == CriticVerdict.INSUFFICIENT_EVIDENCE) {
            log.info("Deterministic Resolution: Critic reported INSUFFICIENT_EVIDENCE.");
            String explanation = "Critic determined evidence is insufficient: " +
                    (criticOutput.getExplanation() != null ? criticOutput.getExplanation() : "insufficient facts in evidence set.");
            ResolutionResponse response = new ResolutionResponse(
                    ResolutionStatus.INSUFFICIENT_EVIDENCE,
                    null,
                    explanation
            );
            response.setUncertaintyNotes(uncertaintyNotes);
            metadata.put("criticVerdict", CriticVerdict.INSUFFICIENT_EVIDENCE.name());
            response.setResolutionMetadata(metadata);
            return response;
        }

        // =========================================================================
        // PHASE 2: Citation & Grounding Validation
        // =========================================================================
        Map<UUID, RetrievedPassageDto> passageByChunkId = passages.stream()
                .filter(p -> p.getChunkId() != null)
                .collect(Collectors.toMap(RetrievedPassageDto::getChunkId, p -> p, (a, b) -> a));
        Set<UUID> validChunkIds = passageByChunkId.keySet();

        if (synthesizerOutput == null || synthesizerOutput.getCandidateAnswer() == null || synthesizerOutput.getCandidateAnswer().isBlank()) {
            ResolutionResponse response = new ResolutionResponse(
                    ResolutionStatus.INSUFFICIENT_EVIDENCE,
                    null,
                    "Insufficient evidence: synthesizer produced no candidate answer."
            );
            response.setUncertaintyNotes(uncertaintyNotes);
            metadata.put("failureReason", "NO_CANDIDATE_ANSWER");
            response.setResolutionMetadata(metadata);
            return response;
        }

        List<AtomicClaimDto> claims = synthesizerOutput.getClaims() != null ? synthesizerOutput.getClaims() : Collections.emptyList();
        List<AtomicClaimDto> acceptedClaims = new ArrayList<>();
        List<RejectedClaimDto> rejectedClaims = new ArrayList<>();
        Set<String> criticUnsupportedIds = (criticOutput != null && criticOutput.getUnsupportedClaimIds() != null)
                ? new HashSet<>(criticOutput.getUnsupportedClaimIds())
                : Collections.emptySet();

        for (AtomicClaimDto claim : claims) {
            List<UUID> cited = claim.getCitedChunkIds() != null ? claim.getCitedChunkIds() : Collections.emptyList();
            
            // Check missing citations
            if (cited.isEmpty()) {
                rejectedClaims.add(new RejectedClaimDto(
                        claim.getClaimId(),
                        claim.getText(),
                        "Claim lacks evidence citations"
                ));
                continue;
            }

            // Check for hallucinated / unverified chunk UUIDs
            List<String> invalidIds = cited.stream()
                    .filter(id -> !validChunkIds.contains(id))
                    .map(UUID::toString)
                    .collect(Collectors.toList());

            if (claim.getInvalidChunkIds() != null && !claim.getInvalidChunkIds().isEmpty()) {
                for (String inv : claim.getInvalidChunkIds()) {
                    if (!invalidIds.contains(inv)) {
                        invalidIds.add(inv);
                    }
                }
            }

            if (!invalidIds.isEmpty()) {
                rejectedClaims.add(new RejectedClaimDto(
                        claim.getClaimId(),
                        claim.getText(),
                        "Hallucinated or unverified evidence chunk ID(s): " + invalidIds,
                        invalidIds
                ));
                continue;
            }

            // Check if flagged unsupported by Critic
            if (claim.getClaimId() != null && criticUnsupportedIds.contains(claim.getClaimId())) {
                rejectedClaims.add(new RejectedClaimDto(
                        claim.getClaimId(),
                        claim.getText(),
                        "Flagged as unsupported by Critic assessment"
                ));
                continue;
            }

            // Check if claim is explicitly marked ungrounded
            if (!claim.isGrounded() && claim.getUnsupportedReason() != null) {
                rejectedClaims.add(new RejectedClaimDto(
                        claim.getClaimId(),
                        claim.getText(),
                        claim.getUnsupportedReason()
                ));
                continue;
            }

            acceptedClaims.add(claim);
        }

        // If claims were provided but none were valid, abstain
        if (!claims.isEmpty() && acceptedClaims.isEmpty()) {
            ResolutionResponse response = new ResolutionResponse(
                    ResolutionStatus.INSUFFICIENT_EVIDENCE,
                    null,
                    "Candidate answer rejected: all atomic claims lacked valid supporting evidence."
            );
            response.setRejectedClaims(rejectedClaims);
            response.setUncertaintyNotes(uncertaintyNotes);
            metadata.put("totalClaims", claims.size());
            metadata.put("acceptedClaims", 0);
            metadata.put("rejectedClaims", rejectedClaims.size());
            response.setResolutionMetadata(metadata);
            return response;
        }

        if (!rejectedClaims.isEmpty()) {
            qualifications.add(rejectedClaims.size() + " claim(s) lacked valid grounding and were excluded.");
        }

        // =========================================================================
        // PHASE 3 & 4 & 5: Contradiction, Scope, Supersession, and Authority
        // =========================================================================
        List<ContradictionFindingDto> contradictions = (criticOutput != null && criticOutput.getContradictions() != null)
                ? criticOutput.getContradictions()
                : Collections.emptyList();

        List<StalenessFindingDto> stalenessFindings = (criticOutput != null && criticOutput.getStalenessFindings() != null)
                ? criticOutput.getStalenessFindings()
                : Collections.emptyList();

        boolean hasUnresolvableConflict = false;
        List<ConflictingPositionDto> conflictingPositions = new ArrayList<>();
        String unresolvableConflictTopic = null;

        for (ContradictionFindingDto contradiction : contradictions) {
            // Rule: Check if conflicting evidence concerns the same scope
            if (!contradiction.isScopeAligned()) {
                qualifications.add("Distinguished differentiated scopes for topic '" + contradiction.getTopic() +
                        "': sources govern different entities, jurisdictions, or parameters.");
                continue;
            }

            // Conflicting evidence IS scope-aligned. Attempt resolution via supersession or authority.
            List<UUID> conflictEvidenceIds = contradiction.getConflictingEvidenceIds();
            List<RetrievedPassageDto> conflictPassages = conflictEvidenceIds.stream()
                    .map(passageByChunkId::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            // If we don't have direct chunk passages, try matching from positions
            if (conflictPassages.size() < 2 && contradiction.getPositions() != null) {
                for (SourcePositionDto pos : contradiction.getPositions()) {
                    if (pos.getPassageId() != null && passageByChunkId.containsKey(pos.getPassageId())) {
                        RetrievedPassageDto p = passageByChunkId.get(pos.getPassageId());
                        if (!conflictPassages.contains(p)) {
                            conflictPassages.add(p);
                        }
                    }
                }
            }

            // Evaluate supersession and authority between competing passages
            boolean contradictionResolved = false;

            if (conflictPassages.size() >= 2) {
                RetrievedPassageDto p1 = conflictPassages.get(0);
                RetrievedPassageDto p2 = conflictPassages.get(1);

                // 1. Explicit Supersession Check
                // Rule: Do not equate latest upload with latest effective policy.
                SupersessionResult supersession = evaluateSupersession(p1, p2, stalenessFindings);
                if (supersession.isResolved()) {
                    contradictionResolved = true;
                    qualifications.add("Resolved via explicit supersession: " + supersession.getExplanation());
                } else {
                    // 2. Authority Rank Check
                    AuthorityLevel rank1 = p1.getParsedAuthorityLevel();
                    AuthorityLevel rank2 = p2.getParsedAuthorityLevel();

                    if (rank1.getRank() > rank2.getRank()) {
                        contradictionResolved = true;
                        qualifications.add("Resolved via authority precedence: " + p1.getSourceTitle() + " (" +
                                rank1.name() + ", rank " + rank1.getRank() + ") takes precedence over " +
                                p2.getSourceTitle() + " (" + rank2.name() + ", rank " + rank2.getRank() + ").");
                    } else if (rank2.getRank() > rank1.getRank()) {
                        contradictionResolved = true;
                        qualifications.add("Resolved via authority precedence: " + p2.getSourceTitle() + " (" +
                                rank2.name() + ", rank " + rank2.getRank() + ") takes precedence over " +
                                p1.getSourceTitle() + " (" + rank1.name() + ", rank " + rank1.getRank() + ").");
                    }
                }
            }

            // If still unresolved: Genuine conflict between equally authoritative, current policies!
            if (!contradictionResolved) {
                hasUnresolvableConflict = true;
                unresolvableConflictTopic = contradiction.getTopic();

                // Populate conflicting positions for preserved conflict
                if (contradiction.getPositions() != null && !contradiction.getPositions().isEmpty()) {
                    for (SourcePositionDto pos : contradiction.getPositions()) {
                        RetrievedPassageDto pass = pos.getPassageId() != null ? passageByChunkId.get(pos.getPassageId()) : null;
                        conflictingPositions.add(new ConflictingPositionDto(
                                pos.getPassageId(),
                                pos.getSourceTitle() != null ? pos.getSourceTitle() : (pass != null ? pass.getSourceTitle() : null),
                                pos.getVersionIdentifier() != null ? pos.getVersionIdentifier() : (pass != null ? pass.getVersionIdentifier() : null),
                                pos.getQuotation(),
                                pos.getAssertion(),
                                pass != null ? pass.getAuthorityLevel() : null,
                                pass != null ? pass.getEffectiveDate() : null,
                                pass != null ? pass.getUploadTimestamp() : null
                        ));
                    }
                } else {
                    for (RetrievedPassageDto p : conflictPassages) {
                        conflictingPositions.add(new ConflictingPositionDto(
                                p.getChunkId(),
                                p.getSourceTitle(),
                                p.getVersionIdentifier(),
                                p.getText(),
                                "Assertion in " + p.getVersionIdentifier(),
                                p.getAuthorityLevel(),
                                p.getEffectiveDate(),
                                p.getUploadTimestamp()
                        ));
                    }
                }
            }
        }

        // Also check if Critic emitted CONFLICT verdict without structured contradiction objects
        if (!hasUnresolvableConflict && contradictions.isEmpty() && criticOutput != null && criticOutput.getVerdict() == CriticVerdict.CONFLICT) {
            hasUnresolvableConflict = true;
            unresolvableConflictTopic = criticOutput.getExplanation() != null ? criticOutput.getExplanation() : "Contradictory policies detected";
            if (conflictingPositions.isEmpty() && criticOutput.getSourcePositions() != null) {
                for (SourcePositionDto pos : criticOutput.getSourcePositions()) {
                    RetrievedPassageDto pass = pos.getPassageId() != null ? passageByChunkId.get(pos.getPassageId()) : null;
                    conflictingPositions.add(new ConflictingPositionDto(
                            pos.getPassageId(),
                            pos.getSourceTitle(),
                            pos.getVersionIdentifier(),
                            pos.getQuotation(),
                            pos.getAssertion(),
                            pass != null ? pass.getAuthorityLevel() : null,
                            pass != null ? pass.getEffectiveDate() : null,
                            pass != null ? pass.getUploadTimestamp() : null
                    ));
                }
            }
        }

        // =========================================================================
        // Conflict Verdict Return: Abstain from guessing or blending
        // =========================================================================
        if (hasUnresolvableConflict) {
            log.warn("Deterministic Resolution: Preserving genuine CONFLICT between equally authoritative sources.");
            String topic = unresolvableConflictTopic != null ? unresolvableConflictTopic : "competing policy mandates";
            ResolutionResponse response = new ResolutionResponse(
                    ResolutionStatus.CONFLICT,
                    null,
                    "Cannot resolve with available information because: Unresolvable contradiction between equally authoritative, equally current sources regarding: " +
                            topic + ". TruthGuard preserves the conflict and abstains from averaging contradictory claims."
            );
            response.setConflictingPositions(conflictingPositions);
            response.setRejectedClaims(rejectedClaims);
            response.setUncertaintyNotes(uncertaintyNotes);
            metadata.put("conflictDetected", true);
            metadata.put("conflictingPositionsCount", conflictingPositions.size());
            response.setResolutionMetadata(metadata);
            return response;
        }

        // =========================================================================
        // Build Valid Evidence References
        // =========================================================================
        Set<UUID> finalChunkIds = new LinkedHashSet<>();
        for (AtomicClaimDto c : acceptedClaims) {
            if (c.getCitedChunkIds() != null) {
                finalChunkIds.addAll(c.getCitedChunkIds());
            }
        }
        if (finalChunkIds.isEmpty() && synthesizerOutput.getEvidenceChunkIds() != null) {
            for (UUID id : synthesizerOutput.getEvidenceChunkIds()) {
                if (validChunkIds.contains(id)) {
                    finalChunkIds.add(id);
                }
            }
        }

        List<EvidenceReferenceDto> evidenceReferences = new ArrayList<>();
        for (UUID chunkId : finalChunkIds) {
            RetrievedPassageDto p = passageByChunkId.get(chunkId);
            if (p != null) {
                String snippet = p.getText() != null && p.getText().length() > 200
                        ? p.getText().substring(0, 200) + "..."
                        : p.getText();
                evidenceReferences.add(new EvidenceReferenceDto(
                        p.getChunkId(),
                        p.getDocumentId(),
                        p.getVersionId(),
                        p.getSourceTitle(),
                        p.getVersionIdentifier(),
                        p.getAuthorityLevel(),
                        p.getEffectiveDate(),
                        p.getPageNumber(),
                        snippet
                ));
            }
        }

        // =========================================================================
        // Final Status: ANSWERED vs ANSWERED_WITH_QUALIFICATION
        // =========================================================================
        boolean hasQualifications = !qualifications.isEmpty()
                || (criticOutput != null && criticOutput.getVerdict() == CriticVerdict.UNSUPPORTED && !acceptedClaims.isEmpty())
                || !rejectedClaims.isEmpty();

        ResolutionStatus finalStatus = hasQualifications
                ? ResolutionStatus.ANSWERED_WITH_QUALIFICATION
                : ResolutionStatus.ANSWERED;

        String finalAnswer = formatFinalAnswer(synthesizerOutput.getCandidateAnswer(), qualifications);
        String explanation = hasQualifications
                ? "Answer validated with deterministic qualifications: " + String.join("; ", qualifications)
                : "Candidate answer is fully substantiated by consistent and authoritative evidence.";

        ResolutionResponse response = new ResolutionResponse(finalStatus, finalAnswer, explanation);
        response.setEvidenceReferences(evidenceReferences);
        response.setConflictingPositions(conflictingPositions);
        response.setRejectedClaims(rejectedClaims);
        response.setUncertaintyNotes(uncertaintyNotes);

        metadata.put("totalClaims", claims.size());
        metadata.put("acceptedClaims", acceptedClaims.size());
        metadata.put("rejectedClaims", rejectedClaims.size());
        metadata.put("evidenceReferencesCount", evidenceReferences.size());
        metadata.put("qualificationsCount", qualifications.size());
        response.setResolutionMetadata(metadata);

        log.info("Deterministic Resolution complete: status={}, acceptedClaims={}, rejectedClaims={}",
                finalStatus, acceptedClaims.size(), rejectedClaims.size());

        return response;
    }

    /**
     * Evaluates explicit supersession relationship between two passages.
     * <p>
     * Criteria:
     * 1. Explicit link: supersedes_version_id or superseded_by_id or staleness finding.
     * 2. Effective date check: superseding version's effectiveDate >= superseded version's effectiveDate.
     * 3. Upload timestamp does NOT override effective policy date!
     */
    private SupersessionResult evaluateSupersession(RetrievedPassageDto p1, RetrievedPassageDto p2,
                                                    List<StalenessFindingDto> stalenessFindings) {
        // Check Staleness findings first
        for (StalenessFindingDto sf : stalenessFindings) {
            if (sf.isHasExplicitSupersession()) {
                if (Objects.equals(sf.getNewerVersionId(), p1.getVersionId()) &&
                        Objects.equals(sf.getOlderVersionId(), p2.getVersionId())) {
                    return new SupersessionResult(true, p1, p2,
                            "Version " + p1.getVersionIdentifier() + " supersedes Version " + p2.getVersionIdentifier() +
                                    " because explicit supersession link: " + sf.getDetails());
                }
                if (Objects.equals(sf.getNewerVersionId(), p2.getVersionId()) &&
                        Objects.equals(sf.getOlderVersionId(), p1.getVersionId())) {
                    return new SupersessionResult(true, p2, p1,
                            "Version " + p2.getVersionIdentifier() + " supersedes Version " + p1.getVersionIdentifier() +
                                    " because explicit supersession link: " + sf.getDetails());
                }
            }
        }

        // Direct metadata supersedes_version_id check
        LocalDate d1 = p1.getParsedEffectiveDate();
        LocalDate d2 = p2.getParsedEffectiveDate();

        // Case 1: p1 supersedes p2
        boolean p1LinksP2 = (p1.getSupersedesVersionId() != null && Objects.equals(p1.getSupersedesVersionId(), p2.getVersionId()))
                || (p2.getSupersededById() != null && Objects.equals(p2.getSupersededById(), p1.getVersionId()));

        if (p1LinksP2) {
            if (d1 == null || d2 == null || !d1.isBefore(d2)) {
                return new SupersessionResult(true, p1, p2,
                        "Version " + p1.getVersionIdentifier() + " supersedes Version " + p2.getVersionIdentifier() +
                                " because explicit supersession metadata links v" + p1.getVersionIdentifier() + " (effective " + p1.getEffectiveDate() + ") to older v" + p2.getVersionIdentifier() + " (effective " + p2.getEffectiveDate() + ")");
            }
        }

        // Case 2: p2 supersedes p1
        boolean p2LinksP1 = (p2.getSupersedesVersionId() != null && Objects.equals(p2.getSupersedesVersionId(), p1.getVersionId()))
                || (p1.getSupersededById() != null && Objects.equals(p1.getSupersededById(), p2.getVersionId()));

        if (p2LinksP1) {
            if (d1 == null || d2 == null || !d2.isBefore(d1)) {
                return new SupersessionResult(true, p2, p1,
                        "Version " + p2.getVersionIdentifier() + " supersedes Version " + p1.getVersionIdentifier() +
                                " because explicit supersession metadata links v" + p2.getVersionIdentifier() + " (effective " + p2.getEffectiveDate() + ") to older v" + p1.getVersionIdentifier() + " (effective " + p1.getEffectiveDate() + ")");
            }
        }

        // Text-based supersession clause verification
        String v1 = p1.getVersionIdentifier() != null ? p1.getVersionIdentifier().replaceFirst("(?i)^v", "") : "";
        String v2 = p2.getVersionIdentifier() != null ? p2.getVersionIdentifier().replaceFirst("(?i)^v", "") : "";

        if (!v2.isEmpty() && p1.getText() != null) {
            String pat = "(?s).*(?i)(supersedes?|amends?|replaces?).*?\\b(policy\\s+|version\\s+|v\\.?)?" + java.util.regex.Pattern.quote(v2) + "\\b.*";
            if (p1.getText().matches(pat)) {
                return new SupersessionResult(true, p1, p2,
                        "Version " + p1.getVersionIdentifier() + " supersedes Version " + p2.getVersionIdentifier() +
                                " because passage text explicitly states supersession of " + p2.getVersionIdentifier());
            }
        }
        if (!v1.isEmpty() && p2.getText() != null) {
            String pat = "(?s).*(?i)(supersedes?|amends?|replaces?).*?\\b(policy\\s+|version\\s+|v\\.?)?" + java.util.regex.Pattern.quote(v1) + "\\b.*";
            if (p2.getText().matches(pat)) {
                return new SupersessionResult(true, p2, p1,
                        "Version " + p2.getVersionIdentifier() + " supersedes Version " + p1.getVersionIdentifier() +
                                " because passage text explicitly states supersession of " + p1.getVersionIdentifier());
            }
        }

        return new SupersessionResult(false, null, null, null);
    }

    private String formatFinalAnswer(String candidateAnswer, List<String> qualifications) {
        if (candidateAnswer == null || candidateAnswer.isBlank()) {
            return null;
        }
        if (qualifications == null || qualifications.isEmpty()) {
            return candidateAnswer.trim();
        }
        StringBuilder sb = new StringBuilder();
        sb.append(candidateAnswer.trim());
        sb.append("\n\n[TruthGuard Qualifications:\n");
        for (String q : qualifications) {
            sb.append("• ").append(q).append("\n");
        }
        sb.append("]");
        return sb.toString();
    }

    private static class SupersessionResult {
        private final boolean resolved;
        private final RetrievedPassageDto superseding;
        private final RetrievedPassageDto superseded;
        private final String explanation;

        public SupersessionResult(boolean resolved, RetrievedPassageDto superseding, RetrievedPassageDto superseded, String explanation) {
            this.resolved = resolved;
            this.superseding = superseding;
            this.superseded = superseded;
            this.explanation = explanation;
        }

        public boolean isResolved() {
            return resolved;
        }

        public RetrievedPassageDto getSuperseding() {
            return superseding;
        }

        public RetrievedPassageDto getSuperseded() {
            return superseded;
        }

        public String getExplanation() {
            return explanation;
        }
    }
}
