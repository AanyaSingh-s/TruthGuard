package com.truthguard.dto.resolution;

import java.util.ArrayList;
import java.util.List;

/**
 * Input request payload for the TruthGuard deterministic Resolution Engine.
 */
public class ResolutionRequest {

    private String question;
    private SynthesizerOutputDto synthesizerOutput;
    private CriticOutputDto criticOutput;
    private List<RetrievedPassageDto> passages = new ArrayList<>();

    public ResolutionRequest() {
    }

    public ResolutionRequest(String question, SynthesizerOutputDto synthesizerOutput,
                             CriticOutputDto criticOutput, List<RetrievedPassageDto> passages) {
        this.question = question;
        this.synthesizerOutput = synthesizerOutput;
        this.criticOutput = criticOutput;
        this.passages = passages != null ? passages : new ArrayList<>();
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
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

    public List<RetrievedPassageDto> getPassages() {
        return passages;
    }

    public void setPassages(List<RetrievedPassageDto> passages) {
        this.passages = passages != null ? passages : new ArrayList<>();
    }
}
