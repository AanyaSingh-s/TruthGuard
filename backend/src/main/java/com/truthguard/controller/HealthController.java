package com.truthguard.controller;

import com.truthguard.dto.HealthResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);
    private static final String SERVICE_NAME = "truthguard-backend";
    private static final String SERVICE_VERSION = "0.1.0";

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> getHealth() {
        log.debug("Received health check request");

        Map<String, String> details = new LinkedHashMap<>();
        details.put("role", "Core Orchestrator & Deterministic Rule Resolution");
        details.put("database", "configured");
        details.put("ai_service_bridge", "configured");

        HealthResponse response = HealthResponse.up(SERVICE_NAME, SERVICE_VERSION, details);
        log.info("Health status reported: {}", response.status());

        return ResponseEntity.ok(response);
    }
}
