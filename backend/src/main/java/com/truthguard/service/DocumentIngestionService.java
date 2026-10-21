package com.truthguard.service;

import com.truthguard.dto.DocumentVersionResponse;
import com.truthguard.exception.*;
import com.truthguard.model.AuthorityLevel;
import com.truthguard.model.Document;
import com.truthguard.model.DocumentVersion;
import com.truthguard.model.ProcessingStatus;
import com.truthguard.repository.DocumentRepository;
import com.truthguard.repository.DocumentVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final StorageService storageService;
    private final TextExtractionClient extractionClient;

    public DocumentIngestionService(
            DocumentRepository documentRepository,
            DocumentVersionRepository versionRepository,
            StorageService storageService,
            TextExtractionClient extractionClient
    ) {
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
        this.storageService = storageService;
        this.extractionClient = extractionClient;
    }

    @Transactional(noRollbackFor = {DocumentParsingException.class, DuplicateContentException.class})
    public DocumentVersionResponse ingestFile(
            UUID documentId,
            MultipartFile file,
            String versionIdentifier,
            LocalDate effectiveDate,
            AuthorityLevel authorityLevel,
            UUID supersedesVersionId
    ) {
        if (versionIdentifier == null || versionIdentifier.isBlank()) {
            throw new IllegalArgumentException("Version identifier is required for upload (e.g. 'v1.0')");
        }

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));

        if (file == null || file.isEmpty()) {
            throw new EmptyFileException("Uploaded file cannot be null or empty (0 bytes)");
        }

        // Validate file extension before processing
        storageService.validateAndExtractExtension(file.getOriginalFilename());

        // 1. Store file safely using internal randomized identifier
        StorageService.StoredFile storedFile = storageService.store(documentId, file);

        // 2. Create initial DocumentVersion with status UPLOADED
        DocumentVersion version = new DocumentVersion(
                UUID.randomUUID(),
                document,
                versionIdentifier.trim(),
                effectiveDate,
                authorityLevel,
                "pending-" + UUID.randomUUID(), // Temporary unique token before hash calculation
                null
        );
        version.setProcessingStatus(ProcessingStatus.UPLOADED);
        version = versionRepository.save(version);
        log.info("Created version {} in status UPLOADED", version.getId());

        // 3. Update status to PROCESSING
        version.setProcessingStatus(ProcessingStatus.PROCESSING);
        version = versionRepository.save(version);

        // 4. Perform text extraction via Python AI service
        TextExtractionClient.ExtractionResult extractionResult;
        try {
            byte[] fileBytes = file.getBytes();
            extractionResult = extractionClient.extractText(storedFile.originalFilename(), fileBytes);
        } catch (IOException e) {
            log.error("Failed to read file bytes for version {}: {}", version.getId(), e.getMessage());
            version.setProcessingStatus(ProcessingStatus.FAILED);
            versionRepository.save(version);
            throw new DocumentIngestionException("Failed to read uploaded file stream: " + e.getMessage(), e);
        } catch (Exception ex) {
            log.error("Extraction failed for version {}: {}", version.getId(), ex.getMessage());
            version.setProcessingStatus(ProcessingStatus.FAILED);
            versionRepository.save(version);
            throw ex;
        }

        // 5. Verify extracted text is non-empty
        String extractedText = extractionResult.extractedText();
        if (extractedText == null || extractedText.isBlank()) {
            log.warn("Extracted text for version {} is empty", version.getId());
            version.setProcessingStatus(ProcessingStatus.FAILED);
            versionRepository.save(version);
            throw new DocumentParsingException("Document extraction completed but yielded no readable text content");
        }

        // 6. Check for duplicate content hash
        String contentHash = extractionResult.contentHash();
        if (versionRepository.existsByDocumentIdAndContentHash(documentId, contentHash)) {
            log.warn("Duplicate content detected for version {}: hash {}", version.getId(), contentHash);
            version.setProcessingStatus(ProcessingStatus.FAILED);
            versionRepository.save(version);
            throw new DuplicateContentException(documentId, contentHash);
        }

        // 7. Handle explicit supersession if provided
        if (supersedesVersionId != null) {
            DocumentVersion olderVersion = versionRepository.findByIdAndDocumentId(supersedesVersionId, documentId)
                    .orElseThrow(() -> new InvalidSupersessionException(
                            "Cannot supersede version " + supersedesVersionId + " because it does not exist for document " + documentId
                    ));

            if (olderVersion.getId().equals(version.getId())) {
                throw new InvalidSupersessionException("A version cannot supersede itself");
            }

            version.setSupersedesVersion(olderVersion);
            olderVersion.setSupersededBy(version);
            versionRepository.save(olderVersion);
            log.info("Explicit supersession linked: version {} supersedes version {}", version.getId(), olderVersion.getId());
        }

        // 8. Finalize version as PROCESSED
        version.setContentHash(contentHash);
        version.setRawContent(extractedText);
        version.setProcessingStatus(ProcessingStatus.PROCESSED);
        DocumentVersion finalizedVersion = versionRepository.save(version);

        document.setUpdatedAt(finalizedVersion.getUploadTimestamp());
        documentRepository.save(document);

        log.info("Successfully ingested version {} ({}) for document {}",
                finalizedVersion.getId(), finalizedVersion.getVersionIdentifier(), documentId);

        return DocumentVersionResponse.fromEntity(finalizedVersion);
    }
}
