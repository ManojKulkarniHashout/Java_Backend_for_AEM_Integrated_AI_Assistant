package com.hashout.aem.aiassistant.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Response model for the AskQuestion chat endpoint
 */
public class ChatResponse {
    
    @JsonProperty("answer")
    private String answer;
    
    @JsonProperty("sources")
    private List<String> sources;
    
    @JsonProperty("confidence")
    private String confidence;
    
    @JsonProperty("timestamp")
    private String timestamp;
    
    // Default constructor
    public ChatResponse() {}
    
    public ChatResponse(String answer, List<String> sources, String confidence, String timestamp) {
        this.answer = answer;
        this.sources = sources;
        this.confidence = confidence;
        this.timestamp = timestamp;
    }
    
    public String getAnswer() {
        return answer;
    }
    
    public void setAnswer(String answer) {
        this.answer = answer;
    }
    
    public List<String> getSources() {
        return sources;
    }
    
    public void setSources(List<String> sources) {
        this.sources = sources;
    }
    
    public String getConfidence() {
        return confidence;
    }
    
    public void setConfidence(String confidence) {
        this.confidence = confidence;
    }
    
    public String getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}