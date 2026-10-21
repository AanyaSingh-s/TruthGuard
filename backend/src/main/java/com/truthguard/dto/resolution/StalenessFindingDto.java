package com.truthguard.dto.resolution;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.UUID;

/**
 * Finding describing the temporal relationship or explicit supersession between versions.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class StalenessFindingDto {

    private UUID olderVersionId;
    private UUID newerVersionId;
    private String olderVersion;
    private String newerVersion;
    private String olderEffectiveDate;
    private String newerEffectiveDate;
    private boolean hasExplicitSupersession;
    private String details;

    public StalenessFindingDto() {
    }

    public StalenessFindingDto(UUID olderVersionId, UUID newerVersionId, String olderVersion,
                               String newerVersion, boolean hasExplicitSupersession, String details) {
        this.olderVersionId = olderVersionId;
        this.newerVersionId = newerVersionId;
        this.olderVersion = olderVersion;
        this.newerVersion = newerVersion;
        this.hasExplicitSupersession = hasExplicitSupersession;
        this.details = details;
    }

    public UUID getOlderVersionId() {
        return olderVersionId;
    }

    public void setOlderVersionId(UUID olderVersionId) {
        this.olderVersionId = olderVersionId;
    }

    public UUID getNewerVersionId() {
        return newerVersionId;
    }

    public void setNewerVersionId(UUID newerVersionId) {
        this.newerVersionId = newerVersionId;
    }

    public String getOlderVersion() {
        return olderVersion;
    }

    public void setOlderVersion(String olderVersion) {
        this.olderVersion = olderVersion;
    }

    public String getNewerVersion() {
        return newerVersion;
    }

    public void setNewerVersion(String newerVersion) {
        this.newerVersion = newerVersion;
    }

    public String getOlderEffectiveDate() {
        return olderEffectiveDate;
    }

    public void setOlderEffectiveDate(String olderEffectiveDate) {
        this.olderEffectiveDate = olderEffectiveDate;
    }

    public String getNewerEffectiveDate() {
        return newerEffectiveDate;
    }

    public void setNewerEffectiveDate(String newerEffectiveDate) {
        this.newerEffectiveDate = newerEffectiveDate;
    }

    public boolean isHasExplicitSupersession() {
        return hasExplicitSupersession;
    }

    public void setHasExplicitSupersession(boolean hasExplicitSupersession) {
        this.hasExplicitSupersession = hasExplicitSupersession;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }
}
