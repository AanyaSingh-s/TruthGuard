package com.truthguard.model;

public enum AuthorityLevel {
    DRAFT(1, "Draft Proposal"),
    WORKING_GROUP(2, "Working Group Consensus"),
    OFFICIAL(3, "Official Policy / Specification"),
    EXECUTIVE(4, "Executive Directive"),
    REGULATORY(5, "Statutory / Regulatory Mandate");

    private final int rank;
    private final String description;

    AuthorityLevel(int rank, String description) {
        this.rank = rank;
        this.description = description;
    }

    public int getRank() {
        return rank;
    }

    public String getDescription() {
        return description;
    }
}
