package com.truthguard.dto;

import com.truthguard.model.Document;
import com.truthguard.model.SourceType;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
    UUID id,
    String title,
    SourceType sourceType,
    String description,
    Instant createdAt,
    Instant updatedAt,
    int versionCount
) {
    public static DocumentResponse fromEntity(Document doc) {
        return new DocumentResponse(
            doc.getId(),
            doc.getTitle(),
            doc.getSourceType(),
            doc.getDescription(),
            doc.getCreatedAt(),
            doc.getUpdatedAt(),
            doc.getVersions() != null ? doc.getVersions().size() : 0
        );
    }
}
