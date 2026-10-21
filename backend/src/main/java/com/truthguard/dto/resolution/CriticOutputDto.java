package com.truthguard.dto.resolution;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Output from the Critic/Adversary Agent evaluating candidate claims and evidence.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CriticOutputDto {

    private CriticVerdict verdict = CriticVerdict.PASS;
    private double confidence = 1.0;
    private List<String> conflictingClaimIds = new ArrayList<>();
    private List<UUID> conflictingEvidenceIds = new ArrayList<>();
    private List<String> unsupportedClaimIds = new ArrayList<>();
    private List<SourcePositionDto> sourcePositions = new ArrayList<>();
    private List<StalenessFindingDto> stalenessFindings = new ArrayList<>();
    private List<ContradictionFindingDto> contradictions = new ArrayList<>();
    private String explanation;
    private List<String> uncertaintyNotes = new ArrayList<>();
    private Map<String, Object> metadata = new HashMap<>();

    public CriticOutputDto() {
    }

    public CriticVerdict getVerdict() {
        return verdict;
    }

    public void setVerdict(CriticVerdict verdict) {
        this.verdict = verdict;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public List<String> getConflictingClaimIds() {
        return conflictingClaimIds;
    }

    public void setConflictingClaimIds(List<String> conflictingClaimIds) {
        this.conflictingClaimIds = conflictingClaimIds != null ? conflictingClaimIds : new ArrayList<>();
    }

    public List<UUID> getConflictingEvidenceIds() {
        return conflictingEvidenceIds;
    }

    public void setConflictingEvidenceIds(List<UUID> conflictingEvidenceIds) {
        this.conflictingEvidenceIds = conflictingEvidenceIds != null ? conflictingEvidenceIds : new ArrayList<>();
    }

    public List<String> getUnsupportedClaimIds() {
        return unsupportedClaimIds;
    }

    public void setUnsupportedClaimIds(List<String> unsupportedClaimIds) {
        this.unsupportedClaimIds = unsupportedClaimIds != null ? unsupportedClaimIds : new ArrayList<>();
    }

    public List<SourcePositionDto> getSourcePositions() {
        return sourcePositions;
    }

    public void setSourcePositions(List<SourcePositionDto> sourcePositions) {
        this.sourcePositions = sourcePositions != null ? sourcePositions : new ArrayList<>();
    }

    public List<StalenessFindingDto> getStalenessFindings() {
        return stalenessFindings;
    }

    public void setStalenessFindings(List<StalenessFindingDto> stalenessFindings) {
        this.stalenessFindings = stalenessFindings != null ? stalenessFindings : new ArrayList<>();
    }

    public List<ContradictionFindingDto> getContradictions() {
        return contradictions;
    }

    public void setContradictions(List<ContradictionFindingDto> contradictions) {
        this.contradictions = contradictions != null ? contradictions : new ArrayList<>();
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public List<String> getUncertaintyNotes() {
        return uncertaintyNotes;
    }

    public void setUncertaintyNotes(List<String> uncertaintyNotes) {
        this.uncertaintyNotes = uncertaintyNotes != null ? uncertaintyNotes : new ArrayList<>();
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata != null ? metadata : new HashMap<>();
    }
}
