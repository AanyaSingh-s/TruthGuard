package com.truthguard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.truthguard.exception.DocumentParsingException;
import com.truthguard.exception.EmptyFileException;
import com.truthguard.exception.UnsupportedFileFormatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Service
public class TextExtractionClient {

    private static final Logger log = LoggerFactory.getLogger(TextExtractionClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String aiServiceUrl;

    public TextExtractionClient(
            @Value("${truthguard.ai-service.url:http://localhost:8000}") String aiServiceUrl,
            ObjectMapper objectMapper
    ) {
        this.aiServiceUrl = aiServiceUrl;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000);
        requestFactory.setReadTimeout(30000);

        this.restClient = RestClient.builder()
                .baseUrl(aiServiceUrl)
                .requestFactory(requestFactory)
                .build();

        log.info("Initialized TextExtractionClient targeting AI service at: {}", aiServiceUrl);
    }

    public record ExtractionResult(
        String format,
        String extractedText,
        int charCount,
        int wordCount,
        String contentHash
    ) {}

    public ExtractionResult extractText(String filename, byte[] fileBytes) {
        log.info("Calling Python AI Service to extract text from '{}' ({} bytes)", filename, fileBytes.length);

        ByteArrayResource resource = new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource);

        try {
            String jsonResponse = restClient.post()
                    .uri("/api/v1/extract-text")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                        String errorBody = new String(response.getBody().readAllBytes());
                        String errorMessage = parseErrorMessage(errorBody);

                        if (response.getStatusCode().value() == 415) {
                            throw new UnsupportedFileFormatException(errorMessage);
                        } else if (response.getStatusCode().value() == 400) {
                            throw new EmptyFileException(errorMessage);
                        } else if (response.getStatusCode().value() == 422) {
                            throw new DocumentParsingException(errorMessage);
                        } else {
                            throw new DocumentParsingException("AI extraction error (" + response.getStatusCode() + "): " + errorMessage);
                        }
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
                        String errorBody = new String(response.getBody().readAllBytes());
                        throw new DocumentParsingException("AI Service internal extraction failure: " + parseErrorMessage(errorBody));
                    })
                    .body(String.class);

            JsonNode root = objectMapper.readTree(jsonResponse);
            return new ExtractionResult(
                    root.path("format").asText(),
                    root.path("extracted_text").asText(),
                    root.path("char_count").asInt(),
                    root.path("word_count").asInt(),
                    root.path("content_hash").asText()
            );

        } catch (UnsupportedFileFormatException | EmptyFileException | DocumentParsingException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to communicate with AI Service at {}: {}", aiServiceUrl, e.getMessage());
            throw new DocumentParsingException("Failed to extract document text: AI Service is unavailable or unreachable (" + e.getMessage() + ")", e);
        }
    }

    public void indexVersion(
            java.util.UUID documentId,
            java.util.UUID versionId,
            String text,
            String sourceTitle,
            String versionIdentifier,
            java.time.LocalDate effectiveDate,
            com.truthguard.model.AuthorityLevel authorityLevel,
            com.truthguard.model.SourceType sourceType,
            java.util.UUID supersedesVersionId
    ) {
        log.info("Requesting Python AI Service to chunk and index version {}", versionId);
        try {
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("document_id", documentId.toString());
            payload.put("version_id", versionId.toString());
            payload.put("text", text);
            if (sourceTitle != null) payload.put("source_title", sourceTitle);
            if (versionIdentifier != null) payload.put("version_identifier", versionIdentifier);
            if (effectiveDate != null) payload.put("effective_date", effectiveDate.toString());
            if (authorityLevel != null) payload.put("authority_level", authorityLevel.name());
            if (sourceType != null) payload.put("source_type", sourceType.name());
            if (supersedesVersionId != null) payload.put("supersedes_version_id", supersedesVersionId.toString());

            restClient.post()
                    .uri("/api/v1/index-version")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Successfully indexed version {} in AI vector store", versionId);
        } catch (Exception e) {
            log.warn("Automatic indexing in AI service failed or skipped for version {}: {}", versionId, e.getMessage());
        }
    }

    private String parseErrorMessage(String errorBody) {
        try {
            JsonNode node = objectMapper.readTree(errorBody);
            if (node.has("detail")) {
                JsonNode detail = node.get("detail");
                if (detail.isObject() && detail.has("message")) {
                    return detail.get("message").asText();
                } else if (detail.isArray() && !detail.isEmpty()) {
                    JsonNode first = detail.get(0);
                    if (first.has("msg")) {
                        return first.get("msg").asText();
                    }
                    return detail.toString();
                }
                return detail.asText();
            }
            if (node.has("message")) {
                return node.get("message").asText();
            }
        } catch (Exception ignored) {
        }
        return errorBody != null && !errorBody.isBlank() ? errorBody : "Unknown extraction error";
    }
}
