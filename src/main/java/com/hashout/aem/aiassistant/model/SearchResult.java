package com.hashout.aem.aiassistant.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Model representing a search result from Azure AI Search
 */
public class SearchResult {
    
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("path")
    private String path;
    
    @JsonProperty("title")
    private String title;
    
    @JsonProperty("content")
    private String content;
    
    @JsonProperty("textChunk")
    private String textChunk;
    
    @JsonProperty("score")
    private double score;
    
    // Default constructor
    public SearchResult() {}
    
    public SearchResult(String id, String path, String title, String content, String textChunk, double score) {
        this.id = id;
        this.path = path;
        this.title = title;
        this.content = content;
        this.textChunk = textChunk;
        this.score = score;
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getPath() {
        return path;
    }
    
    public void setPath(String path) {
        this.path = path;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public String getTextChunk() {
        return textChunk;
    }
    
    public void setTextChunk(String textChunk) {
        this.textChunk = textChunk;
    }
    
    public double getScore() {
        return score;
    }
    
    public void setScore(double score) {
        this.score = score;
    }
}