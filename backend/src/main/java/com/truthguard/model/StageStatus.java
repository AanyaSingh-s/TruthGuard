package com.truthguard.model;

/**
 * Execution status of an individual processing stage in the TruthGuard pipeline.
 */
public enum StageStatus {
    STARTED,
    SUCCESS,
    FAILED,
    SKIPPED
}
