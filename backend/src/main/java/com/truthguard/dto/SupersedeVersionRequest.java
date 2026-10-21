package com.truthguard.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SupersedeVersionRequest(
    @NotNull(message = "supersededByVersionId is required")
    UUID supersededByVersionId
) {
}
