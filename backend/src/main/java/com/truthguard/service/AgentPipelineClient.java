package com.truthguard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.truthguard.dto.pipeline.AiPipelineRequest;
import com.truthguard.dto.pipeline.AiPipelineResponse;
import com.truthguard.exception.AiServiceException;
import com.truthguard.exception.AiServiceTimeoutException;
import com.truthguard.exception.AiServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.SocketTimeoutException;

/**
 * HTTP client for communicating with the Python AI Service integrated pipeline endpoint.
 */
@Service
public class AgentPipelineClient {

    private static final Logger log = LoggerFactory.getLogger(AgentPipelineClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String aiServiceUrl;

    public AgentPipelineClient(
            @Value("${truthguard.ai-service.url:http://localhost:8000}") String aiServiceUrl,
            @Value("${truthguard.ai-service.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${truthguard.ai-service.read-timeout-ms:45000}") int readTimeoutMs,
            ObjectMapper objectMapper
    ) {
        this.aiServiceUrl = aiServiceUrl;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        this.restClient = RestClient.builder()
                .baseUrl(aiServiceUrl)
                .requestFactory(requestFactory)
                .build();

        log.info("Initialized AgentPipelineClient targeting {} (connectTimeout={}ms, readTimeout={}ms)",
                aiServiceUrl, connectTimeoutMs, readTimeoutMs);
    }

    public AiPipelineResponse executePipeline(AiPipelineRequest request) {
        log.info("[{}] Calling Python AI Service pipeline (/api/v1/pipeline/analyze)", request.getCorrelationId());

        try {
            return restClient.post()
                    .uri("/api/v1/pipeline/analyze")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Correlation-ID", request.getCorrelationId())
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        String body = new String(res.getBody().readAllBytes());
                        String msg = parseErrorMessage(body);
                        log.warn("[{}] AI Service rejected request with status {}: {}",
                                request.getCorrelationId(), res.getStatusCode(), msg);
                        throw new IllegalArgumentException("AI Service rejected request: " + msg);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        String body = new String(res.getBody().readAllBytes());
                        String msg = parseErrorMessage(body);
                        int statusVal = res.getStatusCode().value();
                        log.error("[{}] AI Service server error (status {}): {}",
                                request.getCorrelationId(), statusVal, msg);

                        if (statusVal == 504) {
                            throw new AiServiceTimeoutException("AI Service LLM execution timed out: " + msg);
                        } else if (statusVal == 502) {
                            throw new AiServiceException("AI Service LLM provider failed: " + msg);
                        } else {
                            throw new AiServiceException("AI Service internal error (" + statusVal + "): " + msg);
                        }
                    })
                    .body(AiPipelineResponse.class);

        } catch (ResourceAccessException e) {
            if (e.getCause() instanceof SocketTimeoutException) {
                log.error("[{}] Network read timeout talking to AI service at {}", request.getCorrelationId(), aiServiceUrl, e);
                throw new AiServiceTimeoutException("Network timeout contacting AI service at " + aiServiceUrl, e);
            }
            log.error("[{}] Unable to connect to AI service at {}", request.getCorrelationId(), aiServiceUrl, e);
            throw new AiServiceUnavailableException("AI service is unavailable at " + aiServiceUrl + ": " + e.getMessage(), e);
        } catch (AiServiceTimeoutException | AiServiceUnavailableException | AiServiceException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("[{}] Unexpected error calling AI service: {}", request.getCorrelationId(), e.getMessage(), e);
            throw new AiServiceException("Unexpected failure communicating with AI service: " + e.getMessage(), e);
        }
    }

    private String parseErrorMessage(String errorBody) {
        if (errorBody == null || errorBody.isBlank()) {
            return "No error details returned";
        }
        try {
            JsonNode root = objectMapper.readTree(errorBody);
            if (root.has("detail")) {
                JsonNode detail = root.get("detail");
                if (detail.isObject() && detail.has("message")) {
                    return detail.get("message").asText();
                }
                return detail.asText();
            }
            if (root.has("message")) {
                return root.get("message").asText();
            }
        } catch (IOException ignored) {
        }
        return errorBody;
    }
}
