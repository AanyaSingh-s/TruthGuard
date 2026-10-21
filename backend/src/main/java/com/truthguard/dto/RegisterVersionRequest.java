package com.truthguard.dto;

import com.truthguard.model.AuthorityLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record RegisterVersionRequest(
    @NotBlank(message = "Version identifier is required (e.g. v1.0, 2024-Q1)")
    @Size(max = 100, message = "Version identifier must not exceed 100 characters")
    String versionIdentifier,

    LocalDate effectiveDate,

    AuthorityLevel authorityLevel,

    String content,

    @Size(min = 64, max = 64, message = "Content hash must be a 64-character SHA-256 hexadecimal string")
    String contentHash,

    UUID supersedesVersionId
) {
}
