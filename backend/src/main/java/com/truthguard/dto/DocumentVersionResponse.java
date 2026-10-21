package com.truthguard.dto;

import com.truthguard.model.AuthorityLevel;
import com.truthguard.model.DocumentVersion;
import com.truthguard.model.ProcessingStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DocumentVersionResponse(
    UUID id,
    UUID documentId,
    String documentTitle,
    String versionIdentifier,
    Instant uploadTimestamp,
    LocalDate effectiveDate,
    AuthorityLevel authorityLevel,
    Integer authorityRank,
    String contentHash,
    ProcessingStatus processingStatus,
    UUID supersedesVersionId,
    String supersedesVersionIdentifier,
    UUID supersededById,
    String supersededByVersionIdentifier,
    boolean isSuperseded,
    String rawContent
) {
    public static DocumentVersionResponse fromEntity(DocumentVersion v) {
        return new DocumentVersionResponse(
            v.getId(),
            v.getDocument().getId(),
            v.getDocument().getTitle(),
            v.getVersionIdentifier(),
            v.getUploadTimestamp(),
            v.getEffectiveDate(),
            v.getAuthorityLevel(),
            v.getAuthorityLevel() != null ? v.getAuthorityLevel().getRank() : null,
            v.getContentHash(),
            v.getProcessingStatus(),
            v.getSupersedesVersion() != null ? v.getSupersedesVersion().getId() : null,
            v.getSupersedesVersion() != null ? v.getSupersedesVersion().getVersionIdentifier() : null,
            v.getSupersededBy() != null ? v.getSupersededBy().getId() : null,
            v.getSupersededBy() != null ? v.getSupersededBy().getVersionIdentifier() : null,
            v.getSupersededBy() != null,
            v.getRawContent()
        );
    }
}
