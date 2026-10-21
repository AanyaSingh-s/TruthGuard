package com.truthguard.exception;

import java.util.UUID;

public class QueryNotFoundException extends RuntimeException {
    public QueryNotFoundException(UUID queryId) {
        super("Query record not found with ID: " + queryId);
    }
}
