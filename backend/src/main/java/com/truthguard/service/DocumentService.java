package com.truthguard.service;

import com.truthguard.dto.*;
import com.truthguard.exception.*;
import com.truthguard.model.Document;
import com.truthguard.model.DocumentVersion;
import com.truthguard.repository.DocumentRepository;
import com.truthguard.repository.DocumentVersionRepository;
import com.truthguard.util.HashUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;

    public DocumentService(DocumentRepository documentRepository, DocumentVersionRepository versionRepository) {
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
    }

    public DocumentResponse createDocument(CreateDocumentRequest request) {
        log.info("Creating document with title: '{}', sourceType: {}", request.title(), request.sourceType());
        if (documentRepository.existsByTitle(request.title())) {
            throw new DuplicateDocumentTitleException(request.title());
        }

        Document document = new Document(
            UUID.randomUUID(),
            request.title().trim(),
            request.sourceType(),
            request.description()
        );

        Document saved = documentRepository.save(document);
        log.info("Created document with ID: {}", saved.getId());
        return DocumentResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> listDocuments() {
        return documentRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(DocumentResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentResponse getDocument(UUID documentId) {
        Document document = findDocumentOrThrow(documentId);
        return DocumentResponse.fromEntity(document);
    }

    public DocumentVersionResponse registerVersion(UUID documentId, RegisterVersionRequest request) {
        Document document = findDocumentOrThrow(documentId);

        // Derive content hash
        String contentHash;
        if (request.content() != null && !request.content().isBlank()) {
            contentHash = HashUtils.sha256Hex(request.content());
        } else if (request.contentHash() != null && !request.contentHash().isBlank()) {
            contentHash = request.contentHash().toLowerCase().trim();
        } else {
            throw new IllegalArgumentException("Either 'content' or 'contentHash' must be provided for version upload");
        }

        // Duplicate validation using content hash
        if (versionRepository.existsByDocumentIdAndContentHash(documentId, contentHash)) {
            log.warn("Duplicate upload detected: Document {} already has version with content hash {}", documentId, contentHash);
            throw new DuplicateContentException(documentId, contentHash);
        }

        DocumentVersion newVersion = new DocumentVersion(
            UUID.randomUUID(),
            document,
            request.versionIdentifier().trim(),
            request.effectiveDate(),
            request.authorityLevel(),
            contentHash,
            request.content()
        );

        // Handle explicit supersession if provided
        if (request.supersedesVersionId() != null) {
            DocumentVersion olderVersion = versionRepository.findByIdAndDocumentId(request.supersedesVersionId(), documentId)
                    .orElseThrow(() -> new InvalidSupersessionException(
                            "Cannot supersede version " + request.supersedesVersionId() + " because it does not exist for document " + documentId
                    ));

            if (olderVersion.getId().equals(newVersion.getId())) {
                throw new InvalidSupersessionException("A version cannot supersede itself");
            }

            newVersion.setSupersedesVersion(olderVersion);
            olderVersion.setSupersededBy(newVersion);
            versionRepository.save(olderVersion);
            log.info("Explicit supersession established: Version {} supersedes older version {}", newVersion.getId(), olderVersion.getId());
        }

        DocumentVersion savedVersion = versionRepository.save(newVersion);
        document.setUpdatedAt(savedVersion.getUploadTimestamp());
        documentRepository.save(document);

        log.info("Registered version {} ({}) for document {}", savedVersion.getId(), savedVersion.getVersionIdentifier(), documentId);
        return DocumentVersionResponse.fromEntity(savedVersion);
    }

    @Transactional(readOnly = true)
    public List<DocumentVersionResponse> listVersions(UUID documentId, String sortBy, String direction) {
        findDocumentOrThrow(documentId);

        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String property = "effectiveDate".equalsIgnoreCase(sortBy) ? "effectiveDate" : "uploadTimestamp";

        Sort sort = Sort.by(new Sort.Order(dir, property, Sort.NullHandling.NULLS_LAST));
        return versionRepository.findByDocumentId(documentId, sort)
                .stream()
                .map(DocumentVersionResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentVersionResponse getVersion(UUID documentId, UUID versionId) {
        findDocumentOrThrow(documentId);
        DocumentVersion version = versionRepository.findByIdAndDocumentId(versionId, documentId)
                .orElseThrow(() -> new DocumentVersionNotFoundException(documentId, versionId));
        return DocumentVersionResponse.fromEntity(version);
    }

    public DocumentVersionResponse explicitlySupersede(UUID documentId, UUID versionId, UUID supersededByVersionId) {
        findDocumentOrThrow(documentId);

        if (versionId.equals(supersededByVersionId)) {
            throw new InvalidSupersessionException("A version cannot supersede itself");
        }

        DocumentVersion targetVersion = versionRepository.findByIdAndDocumentId(versionId, documentId)
                .orElseThrow(() -> new DocumentVersionNotFoundException(documentId, versionId));

        DocumentVersion supersedingVersion = versionRepository.findByIdAndDocumentId(supersededByVersionId, documentId)
                .orElseThrow(() -> new DocumentVersionNotFoundException(documentId, supersededByVersionId));

        targetVersion.setSupersededBy(supersedingVersion);
        supersedingVersion.setSupersedesVersion(targetVersion);

        DocumentVersion updatedTarget = versionRepository.save(targetVersion);
        versionRepository.save(supersedingVersion);

        log.info("Explicit supersession configured: Version {} is now superseded by Version {}", versionId, supersededByVersionId);
        return DocumentVersionResponse.fromEntity(updatedTarget);
    }

    private Document findDocumentOrThrow(UUID documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
    }
}
