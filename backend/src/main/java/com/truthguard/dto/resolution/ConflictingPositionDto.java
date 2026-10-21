package com.truthguard.dto.resolution;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.UUID;

/**
 * Representation of a conflicting position between source documents or policies.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ConflictingPositionDto {

    private UUID passageId;
    private String sourceTitle;
    private String versionIdentifier;
    private String quotation;
    private String assertion;
    private String authorityLevel;
    private String effectiveDate;
    private String uploadTimestamp;

    public ConflictingPositionDto() {
    }

    public ConflictingPositionDto(UUID passageId, String sourceTitle, String versionIdentifier,
                                  String quotation, String assertion, String authorityLevel,
                                  String effectiveDate, String uploadTimestamp) {
        this.passageId = passageId;
        this.sourceTitle = sourceTitle;
        this.versionIdentifier = versionIdentifier;
        this.quotation = quotation;
        this.assertion = assertion;
        this.authorityLevel = authorityLevel;
        this.effectiveDate = effectiveDate;
        this.uploadTimestamp = uploadTimestamp;
    }

    public UUID getPassageId() {
        return passageId;
    }

    public void setPassageId(UUID passageId) {
        this.passageId = passageId;
    }

    public String getSourceTitle() {
        return sourceTitle;
    }

    public void setSourceTitle(String sourceTitle) {
        this.sourceTitle = sourceTitle;
    }

    public String getVersionIdentifier() {
        return versionIdentifier;
    }

    public void setVersionIdentifier(String versionIdentifier) {
        this.versionIdentifier = versionIdentifier;
    }

    public String getQuotation() {
        return quotation;
    }

    public void setQuotation(String quotation) {
        this.quotation = quotation;
    }

    public String getAssertion() {
        return assertion;
    }

    public void setAssertion(String assertion) {
        this.assertion = assertion;
    }

    public String getAuthorityLevel() {
        return authorityLevel;
    }

    public void setAuthorityLevel(String authorityLevel) {
        this.authorityLevel = authorityLevel;
    }

    public String getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(String effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public String getUploadTimestamp() {
        return uploadTimestamp;
    }

    public void setUploadTimestamp(String uploadTimestamp) {
        this.uploadTimestamp = uploadTimestamp;
    }
}
