package com.truthguard.dto.resolution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Structured output produced by the TruthGuard deterministic Resolution Engine.
 */
public class ResolutionResponse {

    private ResolutionStatus status;
    private String finalAnswer;
    private List<EvidenceReferenceDto> evidenceReferences = new ArrayList<>();
    private List<ConflictingPositionDto> conflictingPositions = new ArrayList<>();
    private String explanation;
    private List<RejectedClaimDto> rejectedClaims = new ArrayList<>();
    private List<String> uncertaintyNotes = new ArrayList<>();
    private Map<String, Object> resolutionMetadata = new HashMap<>();

    public ResolutionResponse() {
    }

    public ResolutionResponse(ResolutionStatus status, String finalAnswer, String explanation) {
        this.status = status;
        this.finalAnswer = finalAnswer;
        this.explanation = explanation;
    }

    public ResolutionStatus getStatus() {
        return status;
    }

    public void setStatus(ResolutionStatus status) {
        this.status = status;
    }

    public String getFinalAnswer() {
        return finalAnswer;
    }

    public void setFinalAnswer(String finalAnswer) {
        this.finalAnswer = finalAnswer;
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

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
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

    public Map<String, Object> getResolutionMetadata() {
        return resolutionMetadata;
    }

    public void setResolutionMetadata(Map<String, Object> resolutionMetadata) {
        this.resolutionMetadata = resolutionMetadata != null ? resolutionMetadata : new HashMap<>();
    }
}
