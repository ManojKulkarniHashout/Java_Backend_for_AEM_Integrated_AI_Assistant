package com.hashout.aem.aiassistant.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request model for the AskQuestion chat endpoint
 */
public class ChatRequest {
    
    @JsonProperty("question")
    private String question;
    
    // Default constructor
    public ChatRequest() {}
    
    public ChatRequest(String question) {
        this.question = question;
    }
    
    public String getQuestion() {
        return question;
    }
    
    public void setQuestion(String question) {
        this.question = question;
    }
    
    /**
     * Validates the request
     */
    public boolean isValid() {
        return question != null && !question.trim().isEmpty();
    }
}