package com.truthguard.dto.resolution;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.UUID;

/**
 * An individual assertion or stance identified in a source passage.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class SourcePositionDto {

    private UUID passageId;
    private String sourceTitle;
    private String versionIdentifier;
    private String quotation;
    private String assertion;

    public SourcePositionDto() {
    }

    public SourcePositionDto(UUID passageId, String sourceTitle, String versionIdentifier, String quotation, String assertion) {
        this.passageId = passageId;
        this.sourceTitle = sourceTitle;
        this.versionIdentifier = versionIdentifier;
        this.quotation = quotation;
        this.assertion = assertion;
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
}
