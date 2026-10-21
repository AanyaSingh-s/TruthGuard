package com.truthguard.dto.resolution;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Structured output received from TruthGuard Synthesizer Agent.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class SynthesizerOutputDto {

    private String question;
    private String candidateAnswer;
    private List<AtomicClaimDto> claims = new ArrayList<>();
    private int totalClaims;
    private int supportedClaimsCount;
    private int unsupportedClaimsCount;
    private boolean isFullyGrounded;
    private List<String> uncertaintyNotes = new ArrayList<>();
    private List<String> missingInformation = new ArrayList<>();
    private List<UUID> evidenceChunkIds = new ArrayList<>();

    public SynthesizerOutputDto() {
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getCandidateAnswer() {
        return candidateAnswer;
    }

    public void setCandidateAnswer(String candidateAnswer) {
        this.candidateAnswer = candidateAnswer;
    }

    public List<AtomicClaimDto> getClaims() {
        return claims;
    }

    public void setClaims(List<AtomicClaimDto> claims) {
        this.claims = claims != null ? claims : new ArrayList<>();
        this.totalClaims = this.claims.size();
    }

    public int getTotalClaims() {
        return totalClaims;
    }

    public void setTotalClaims(int totalClaims) {
        this.totalClaims = totalClaims;
    }

    public int getSupportedClaimsCount() {
        return supportedClaimsCount;
    }

    public void setSupportedClaimsCount(int supportedClaimsCount) {
        this.supportedClaimsCount = supportedClaimsCount;
    }

    public int getUnsupportedClaimsCount() {
        return unsupportedClaimsCount;
    }

    public void setUnsupportedClaimsCount(int unsupportedClaimsCount) {
        this.unsupportedClaimsCount = unsupportedClaimsCount;
    }

    public boolean isFullyGrounded() {
        return isFullyGrounded;
    }

    public void setFullyGrounded(boolean fullyGrounded) {
        isFullyGrounded = fullyGrounded;
    }

    public List<String> getUncertaintyNotes() {
        return uncertaintyNotes;
    }

    public void setUncertaintyNotes(List<String> uncertaintyNotes) {
        this.uncertaintyNotes = uncertaintyNotes != null ? uncertaintyNotes : new ArrayList<>();
    }

    public List<String> getMissingInformation() {
        return missingInformation;
    }

    public void setMissingInformation(List<String> missingInformation) {
        this.missingInformation = missingInformation != null ? missingInformation : new ArrayList<>();
    }

    public List<UUID> getEvidenceChunkIds() {
        return evidenceChunkIds;
    }

    public void setEvidenceChunkIds(List<UUID> evidenceChunkIds) {
        this.evidenceChunkIds = evidenceChunkIds != null ? evidenceChunkIds : new ArrayList<>();
    }
}
