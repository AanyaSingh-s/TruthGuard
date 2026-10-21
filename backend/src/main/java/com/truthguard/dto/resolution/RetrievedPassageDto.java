package com.truthguard.dto.resolution;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.truthguard.model.AuthorityLevel;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Ranked evidence passage retrieved from the vector/lexical index.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class RetrievedPassageDto {

    private UUID chunkId;
    private UUID documentId;
    private UUID versionId;
    private String versionIdentifier;
    private int chunkIndex;
    private String text;
    private double score;
    private Double semanticScore;
    private Double keywordScore;
    private String sourceTitle;
    private String sourceType;
    private Integer pageNumber;
    private String effectiveDate;
    private String uploadTimestamp;
    private String authorityLevel;
    private UUID supersedesVersionId;
    private UUID supersededById;
    private Map<String, Object> metadata = new HashMap<>();

    public RetrievedPassageDto() {
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

    public String getVersionIdentifier() {
        return versionIdentifier;
    }

    public void setVersionIdentifier(String versionIdentifier) {
        this.versionIdentifier = versionIdentifier;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public Double getSemanticScore() {
        return semanticScore;
    }

    public void setSemanticScore(Double semanticScore) {
        this.semanticScore = semanticScore;
    }

    public Double getKeywordScore() {
        return keywordScore;
    }

    public void setKeywordScore(Double keywordScore) {
        this.keywordScore = keywordScore;
    }

    public String getSourceTitle() {
        return sourceTitle;
    }

    public void setSourceTitle(String sourceTitle) {
        this.sourceTitle = sourceTitle;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public Integer getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(Integer pageNumber) {
        this.pageNumber = pageNumber;
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

    public String getAuthorityLevel() {
        return authorityLevel;
    }

    public void setAuthorityLevel(String authorityLevel) {
        this.authorityLevel = authorityLevel;
    }

    public UUID getSupersedesVersionId() {
        return supersedesVersionId;
    }

    public void setSupersedesVersionId(UUID supersedesVersionId) {
        this.supersedesVersionId = supersedesVersionId;
    }

    public UUID getSupersededById() {
        return supersededById;
    }

    public void setSupersededById(UUID supersededById) {
        this.supersededById = supersededById;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata != null ? metadata : new HashMap<>();
    }

    /**
     * Safely parse effectiveDate into LocalDate.
     */
    public LocalDate getParsedEffectiveDate() {
        if (effectiveDate == null || effectiveDate.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(effectiveDate.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Safely parse uploadTimestamp into Instant.
     */
    public Instant getParsedUploadTimestamp() {
        if (uploadTimestamp == null || uploadTimestamp.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(uploadTimestamp.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Parse AuthorityLevel enum, defaulting to OFFICIAL if unspecified or unrecognized.
     */
    public AuthorityLevel getParsedAuthorityLevel() {
        if (authorityLevel == null || authorityLevel.isBlank()) {
            return AuthorityLevel.OFFICIAL;
        }
        try {
            return AuthorityLevel.valueOf(authorityLevel.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return AuthorityLevel.OFFICIAL;
        }
    }
}
