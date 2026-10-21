package com.truthguard.exception;

import java.util.UUID;

public class DuplicateContentException extends RuntimeException {
    public DuplicateContentException(UUID documentId, String contentHash) {
        super("Document " + documentId + " already contains an identical version with content hash: " + contentHash);
    }

    public DuplicateContentException(String message) {
        super(message);
    }
}
