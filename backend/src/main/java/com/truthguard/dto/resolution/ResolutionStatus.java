package com.truthguard.dto.resolution;

/**
 * Status outcomes of the TruthGuard deterministic Resolution Engine.
 */
public enum ResolutionStatus {
    /**
     * All claims are substantiated by consistent evidence with no unresolvable contradictions.
     */
    ANSWERED,

    /**
     * An answer is permitted with explicit qualifications (e.g., supersession of an older policy,
     * or authority-based resolution).
     */
    ANSWERED_WITH_QUALIFICATION,

    /**
     * Unresolvable contradiction between equally authoritative, equally current sources on the same scope.
     * System abstains from guessing or blending contradictory claims.
     */
    CONFLICT,

    /**
     * Retrieved evidence is empty or does not contain facts necessary to answer the question.
     */
    INSUFFICIENT_EVIDENCE
}
