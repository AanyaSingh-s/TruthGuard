package com.truthguard.model;

/**
 * Lifecycle status of an orchestrated TruthGuard verification query.
 */
public enum QueryStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    ABSTAINED,
    FAILED
}
