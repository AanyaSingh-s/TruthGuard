package com.truthguard.dto.resolution;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Details of a contradiction detected between evidence passages or candidate claims.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ContradictionFindingDto {

    private List<String> conflictingClaimIds = new ArrayList<>();
    private List<UUID> conflictingEvidenceIds = new ArrayList<>();
    private String topic;
    private List<SourcePositionDto> positions = new ArrayList<>();
    private boolean scopeAligned = true;
    private String explanation;

    public ContradictionFindingDto() {
    }

    public ContradictionFindingDto(String topic, boolean scopeAligned, String explanation) {
        this.topic = topic;
        this.scopeAligned = scopeAligned;
        this.explanation = explanation;
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

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public List<SourcePositionDto> getPositions() {
        return positions;
    }

    public void setPositions(List<SourcePositionDto> positions) {
        this.positions = positions != null ? positions : new ArrayList<>();
    }

    public boolean isScopeAligned() {
        return scopeAligned;
    }

    public void setScopeAligned(boolean scopeAligned) {
        this.scopeAligned = scopeAligned;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }
}
