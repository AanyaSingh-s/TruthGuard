package com.truthguard.dto.query;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

public class QuestionQueryRequest {

    @NotBlank(message = "Question cannot be blank")
    private String question;

    private String correlationId;
    private Integer topK = 5;
    private Double minScore = 0.0;
    private Double semanticWeight = 0.7;
    private List<UUID> documentIds;
    private List<UUID> versionIds;
    private List<String> authorityLevels;

    public QuestionQueryRequest() {
    }

    public QuestionQueryRequest(String question) {
        this.question = question;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    public Double getMinScore() {
        return minScore;
    }

    public void setMinScore(Double minScore) {
        this.minScore = minScore;
    }

    public Double getSemanticWeight() {
        return semanticWeight;
    }

    public void setSemanticWeight(Double semanticWeight) {
        this.semanticWeight = semanticWeight;
    }

    public List<UUID> getDocumentIds() {
        return documentIds;
    }

    public void setDocumentIds(List<UUID> documentIds) {
        this.documentIds = documentIds;
    }

    public List<UUID> getVersionIds() {
        return versionIds;
    }

    public void setVersionIds(List<UUID> versionIds) {
        this.versionIds = versionIds;
    }

    public List<String> getAuthorityLevels() {
        return authorityLevels;
    }

    public void setAuthorityLevels(List<String> authorityLevels) {
        this.authorityLevels = authorityLevels;
    }
}
