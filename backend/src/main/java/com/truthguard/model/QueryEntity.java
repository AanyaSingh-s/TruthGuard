package com.truthguard.model;

import com.truthguard.dto.resolution.ResolutionStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Persisted query record tracking the lifecycle and resolution of an orchestrated QA request.
 */
@Entity
@Table(name = "queries")
public class QueryEntity {

    @Id
    private UUID id;

    @Column(name = "correlation_id", nullable = false, length = 100)
    private String correlationId;

    @Column(name = "question", nullable = false, columnDefinition = "TEXT")
    private String question;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private QueryStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_status", length = 50)
    private ResolutionStatus resolutionStatus;

    @Column(name = "final_answer", columnDefinition = "TEXT")
    private String finalAnswer;

    @Column(name = "explanation", columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "total_duration_ms")
    private Long totalDurationMs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @OneToMany(mappedBy = "query", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("createdAt ASC")
    private List<QueryStageTraceEntity> stageTraces = new ArrayList<>();

    public QueryEntity() {
    }

    public QueryEntity(UUID id, String correlationId, String question, QueryStatus status, Instant createdAt) {
        this.id = id != null ? id : UUID.randomUUID();
        this.correlationId = correlationId;
        this.question = question;
        this.status = status;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public void addStageTrace(String stageName, StageStatus stageStatus, Long durationMs, String details) {
        QueryStageTraceEntity trace = new QueryStageTraceEntity(
                UUID.randomUUID(),
                this,
                stageName,
                stageStatus,
                durationMs,
                details,
                Instant.now()
        );
        this.stageTraces.add(trace);
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public QueryStatus getStatus() {
        return status;
    }

    public void setStatus(QueryStatus status) {
        this.status = status;
    }

    public ResolutionStatus getResolutionStatus() {
        return resolutionStatus;
    }

    public void setResolutionStatus(ResolutionStatus resolutionStatus) {
        this.resolutionStatus = resolutionStatus;
    }

    public String getFinalAnswer() {
        return finalAnswer;
    }

    public void setFinalAnswer(String finalAnswer) {
        this.finalAnswer = finalAnswer;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public Long getTotalDurationMs() {
        return totalDurationMs;
    }

    public void setTotalDurationMs(Long totalDurationMs) {
        this.totalDurationMs = totalDurationMs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public List<QueryStageTraceEntity> getStageTraces() {
        return stageTraces;
    }

    public void setStageTraces(List<QueryStageTraceEntity> stageTraces) {
        this.stageTraces = stageTraces != null ? stageTraces : new ArrayList<>();
    }
}
