package com.truthguard.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Audit record of an individual processing stage in the TruthGuard verification pipeline.
 */
@Entity
@Table(name = "query_stage_traces")
public class QueryStageTraceEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "query_id", nullable = false)
    private QueryEntity query;

    @Column(name = "stage_name", nullable = false, length = 50)
    private String stageName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private StageStatus status;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public QueryStageTraceEntity() {
    }

    public QueryStageTraceEntity(UUID id, QueryEntity query, String stageName, StageStatus status, Long durationMs, String details, Instant createdAt) {
        this.id = id != null ? id : UUID.randomUUID();
        this.query = query;
        this.stageName = stageName;
        this.status = status;
        this.durationMs = durationMs;
        this.details = details;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public QueryEntity getQuery() {
        return query;
    }

    public void setQuery(QueryEntity query) {
        this.query = query;
    }

    public String getStageName() {
        return stageName;
    }

    public void setStageName(String stageName) {
        this.stageName = stageName;
    }

    public StageStatus getStatus() {
        return status;
    }

    public void setStatus(StageStatus status) {
        this.status = status;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
