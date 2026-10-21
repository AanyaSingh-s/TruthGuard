package com.truthguard.dto.pipeline;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Request payload sent to Python AI Service (/api/v1/pipeline/analyze).
 */
public class AiPipelineRequest {

    private String question;

    @JsonProperty("correlation_id")
    private String correlationId;

    @JsonProperty("top_k")
    private Integer topK = 5;

    @JsonProperty("min_score")
    private Double minScore = 0.0;

    @JsonProperty("semantic_weight")
    private Double semanticWeight = 0.7;

    @JsonProperty("ranking_method")
    private String rankingMethod = "hybrid_linear";

    private Double temperature = 0.0;

    @JsonProperty("max_tokens")
    private Integer maxTokens = 1024;

    private Map<String, Object> filters;

    public AiPipelineRequest() {
    }

    public AiPipelineRequest(String question, String correlationId) {
        this.question = question;
        this.correlationId = correlationId;
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

    public String getRankingMethod() {
        return rankingMethod;
    }

    public void setRankingMethod(String rankingMethod) {
        this.rankingMethod = rankingMethod;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public Map<String, Object> getFilters() {
        return filters;
    }

    public void setFilters(Map<String, Object> filters) {
        this.filters = filters;
    }
}
