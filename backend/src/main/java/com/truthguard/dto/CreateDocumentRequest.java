package com.truthguard.dto;

import com.truthguard.model.SourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateDocumentRequest(
    @NotBlank(message = "Document title is required")
    @Size(max = 255, message = "Document title must not exceed 255 characters")
    String title,

    @NotNull(message = "Document sourceType is required")
    SourceType sourceType,

    String description
) {
}
