package com.truthguard.dto.pipeline;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.truthguard.dto.resolution.CriticOutputDto;
import com.truthguard.dto.resolution.RetrievedPassageDto;
import com.truthguard.dto.resolution.SynthesizerOutputDto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Structured response returned by Python AI Service (/api/v1/pipeline/analyze).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AiPipelineResponse {

    private String question;
    private String correlationId;
    private List<RetrievedPassageDto> passages = new ArrayList<>();
    private SynthesizerOutputDto synthesizerOutput;
    private CriticOutputDto criticOutput;
    private Map<String, Object> trace = new HashMap<>();

    public AiPipelineResponse() {
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

    public List<RetrievedPassageDto> getPassages() {
        return passages;
    }

    public void setPassages(List<RetrievedPassageDto> passages) {
        this.passages = passages != null ? passages : new ArrayList<>();
    }

    public SynthesizerOutputDto getSynthesizerOutput() {
        return synthesizerOutput;
    }

    public void setSynthesizerOutput(SynthesizerOutputDto synthesizerOutput) {
        this.synthesizerOutput = synthesizerOutput;
    }

    public CriticOutputDto getCriticOutput() {
        return criticOutput;
    }

    public void setCriticOutput(CriticOutputDto criticOutput) {
        this.criticOutput = criticOutput;
    }

    public Map<String, Object> getTrace() {
        return trace;
    }

    public void setTrace(Map<String, Object> trace) {
        this.trace = trace != null ? trace : new HashMap<>();
    }
}
