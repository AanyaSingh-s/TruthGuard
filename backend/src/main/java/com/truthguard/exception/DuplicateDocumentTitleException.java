package com.truthguard.exception;

public class DuplicateDocumentTitleException extends RuntimeException {
    public DuplicateDocumentTitleException(String title) {
        super("A document with title '" + title + "' already exists");
    }
}
