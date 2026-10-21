package com.truthguard.dto.resolution;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.ArrayList;
import java.util.List;

/**
 * Representation of an individual claim rejected during deterministic resolution.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class RejectedClaimDto {

    private String claimId;
    private String claimText;
    private String rejectionReason;
    private List<String> invalidChunkIds = new ArrayList<>();

    public RejectedClaimDto() {
    }

    public RejectedClaimDto(String claimId, String claimText, String rejectionReason) {
        this.claimId = claimId;
        this.claimText = claimText;
        this.rejectionReason = rejectionReason;
    }

    public RejectedClaimDto(String claimId, String claimText, String rejectionReason, List<String> invalidChunkIds) {
        this.claimId = claimId;
        this.claimText = claimText;
        this.rejectionReason = rejectionReason;
        this.invalidChunkIds = invalidChunkIds != null ? invalidChunkIds : new ArrayList<>();
    }

    public String getClaimId() {
        return claimId;
    }

    public void setClaimId(String claimId) {
        this.claimId = claimId;
    }

    public String getClaimText() {
        return claimText;
    }

    public void setClaimText(String claimText) {
        this.claimText = claimText;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public List<String> getInvalidChunkIds() {
        return invalidChunkIds;
    }

    public void setInvalidChunkIds(List<String> invalidChunkIds) {
        this.invalidChunkIds = invalidChunkIds != null ? invalidChunkIds : new ArrayList<>();
    }
}
