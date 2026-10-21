package com.truthguard.controller;

import com.truthguard.dto.*;
import com.truthguard.service.DocumentService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@CrossOrigin(origins = "*")
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

    private final DocumentService documentService;
    private final com.truthguard.service.DocumentIngestionService ingestionService;

    public DocumentController(DocumentService documentService, com.truthguard.service.DocumentIngestionService ingestionService) {
        this.documentService = documentService;
        this.ingestionService = ingestionService;
    }

    @PostMapping
    public ResponseEntity<DocumentResponse> createDocument(@Valid @RequestBody CreateDocumentRequest request) {
        log.info("REST request to create document: {}", request.title());
        DocumentResponse response = documentService.createDocument(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<DocumentResponse>> listDocuments() {
        log.debug("REST request to list all documents");
        return ResponseEntity.ok(documentService.listDocuments());
    }

    @GetMapping("/{documentId}")
    public ResponseEntity<DocumentResponse> getDocument(@PathVariable UUID documentId) {
        log.debug("REST request to retrieve document: {}", documentId);
        return ResponseEntity.ok(documentService.getDocument(documentId));
    }

    @PostMapping("/{documentId}/versions")
    public ResponseEntity<DocumentVersionResponse> registerVersion(
            @PathVariable UUID documentId,
            @Valid @RequestBody RegisterVersionRequest request
    ) {
        log.info("REST request to register version '{}' for document {}", request.versionIdentifier(), documentId);
        DocumentVersionResponse response = documentService.registerVersion(documentId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping(value = "/{documentId}/versions/upload", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentVersionResponse> uploadVersion(
            @PathVariable UUID documentId,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam("versionIdentifier") String versionIdentifier,
            @RequestParam(value = "effectiveDate", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate effectiveDate,
            @RequestParam(value = "authorityLevel", required = false) com.truthguard.model.AuthorityLevel authorityLevel,
            @RequestParam(value = "supersedesVersionId", required = false) UUID supersedesVersionId
    ) {
        log.info("REST request to upload file '{}' as version '{}' for document {}",
                file != null ? file.getOriginalFilename() : "null", versionIdentifier, documentId);
        DocumentVersionResponse response = ingestionService.ingestFile(
                documentId, file, versionIdentifier, effectiveDate, authorityLevel, supersedesVersionId
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{documentId}/versions")
    public ResponseEntity<List<DocumentVersionResponse>> listVersions(
            @PathVariable UUID documentId,
            @RequestParam(required = false, defaultValue = "uploadTimestamp") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String direction
    ) {
        log.debug("REST request to list versions for document {} (sortBy: {}, direction: {})", documentId, sortBy, direction);
        return ResponseEntity.ok(documentService.listVersions(documentId, sortBy, direction));
    }

    @GetMapping("/{documentId}/versions/{versionId}")
    public ResponseEntity<DocumentVersionResponse> getVersion(
            @PathVariable UUID documentId,
            @PathVariable UUID versionId
    ) {
        log.debug("REST request to retrieve version {} for document {}", versionId, documentId);
        return ResponseEntity.ok(documentService.getVersion(documentId, versionId));
    }

    @PostMapping("/{documentId}/versions/{versionId}/supersede")
    public ResponseEntity<DocumentVersionResponse> explicitlySupersede(
            @PathVariable UUID documentId,
            @PathVariable UUID versionId,
            @Valid @RequestBody SupersedeVersionRequest request
    ) {
        log.info("REST request to mark version {} as superseded by version {} in document {}",
                versionId, request.supersededByVersionId(), documentId);
        DocumentVersionResponse response = documentService.explicitlySupersede(documentId, versionId, request.supersededByVersionId());
        return ResponseEntity.ok(response);
    }
}
