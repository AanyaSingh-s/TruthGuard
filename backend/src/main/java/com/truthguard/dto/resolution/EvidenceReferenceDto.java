package com.truthguard.dto.resolution;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.UUID;

/**
 * Validated evidence reference cited in support of an answer.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class EvidenceReferenceDto {

    private UUID chunkId;
    private UUID documentId;
    private UUID versionId;
    private String sourceTitle;
    private String versionIdentifier;
    private String authorityLevel;
    private String effectiveDate;
    private Integer pageNumber;
    private String snippet;

    public EvidenceReferenceDto() {
    }

    public EvidenceReferenceDto(UUID chunkId, UUID documentId, UUID versionId, String sourceTitle,
                                String versionIdentifier, String authorityLevel, String effectiveDate,
                                Integer pageNumber, String snippet) {
        this.chunkId = chunkId;
        this.documentId = documentId;
        this.versionId = versionId;
        this.sourceTitle = sourceTitle;
        this.versionIdentifier = versionIdentifier;
        this.authorityLevel = authorityLevel;
        this.effectiveDate = effectiveDate;
        this.pageNumber = pageNumber;
        this.snippet = snippet;
    }

    public UUID getChunkId() {
        return chunkId;
    }

    public void setChunkId(UUID chunkId) {
        this.chunkId = chunkId;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public void setDocumentId(UUID documentId) {
        this.documentId = documentId;
    }

    public UUID getVersionId() {
        return versionId;
    }

    public void setVersionId(UUID versionId) {
        this.versionId = versionId;
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

    public Integer getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(Integer pageNumber) {
        this.pageNumber = pageNumber;
    }

    public String getSnippet() {
        return snippet;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }
}
