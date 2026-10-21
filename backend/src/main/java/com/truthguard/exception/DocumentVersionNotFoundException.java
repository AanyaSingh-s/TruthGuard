package com.truthguard.exception;

import java.util.UUID;

public class DocumentVersionNotFoundException extends RuntimeException {
    public DocumentVersionNotFoundException(UUID versionId) {
        super("Document version not found with ID: " + versionId);
    }

    public DocumentVersionNotFoundException(UUID documentId, UUID versionId) {
        super("Document version not found with ID: " + versionId + " for document: " + documentId);
    }
}
