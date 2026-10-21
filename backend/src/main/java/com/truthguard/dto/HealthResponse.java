package com.truthguard.dto;

import java.time.Instant;
import java.util.Map;

public record HealthResponse(
    String status,
    String service,
    String version,
    Instant timestamp,
    Map<String, String> details
) {
    public static HealthResponse up(String service, String version, Map<String, String> details) {
        return new HealthResponse("UP", service, version, Instant.now(), details);
    }
}
