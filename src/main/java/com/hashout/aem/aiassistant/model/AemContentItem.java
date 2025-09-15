package com.hashout.aem.aiassistant.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * POJO representing an AEM content item from the Data Export Servlet
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AemContentItem {
    
    @JsonProperty("jcr:path")
    private String path;
    
    @JsonProperty("jcr:title")
    private String title;
    
    @JsonProperty("jcr:description")
    private String description;
    
    @JsonProperty("cq:lastModified")
    private String lastModified;
    
    @JsonProperty("cq:tags")
    private String[] tags;
    
    private String content;
    private String contentType;
    private String author;
    
    // Default constructor
    public AemContentItem() {}
    
    // Getters and setters
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
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getLastModified() {
        return lastModified;
    }
    
    public void setLastModified(String lastModified) {
        this.lastModified = lastModified;
    }
    
    public String[] getTags() {
        return tags;
    }
    
    public void setTags(String[] tags) {
        this.tags = tags;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public String getContentType() {
        return contentType;
    }
    
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }
    
    public String getAuthor() {
        return author;
    }
    
    public void setAuthor(String author) {
        this.author = author;
    }
    
    /**
     * Creates a human-readable text chunk for embedding
     */
    public String createTextChunk() {
        StringBuilder chunk = new StringBuilder();
        
        if (title != null && !title.isEmpty()) {
            chunk.append("Title: ").append(title).append("\n");
        }
        
        if (description != null && !description.isEmpty()) {
            chunk.append("Description: ").append(description).append("\n");
        }
        
        if (content != null && !content.isEmpty()) {
            chunk.append("Content: ").append(content).append("\n");
        }
        
        if (tags != null && tags.length > 0) {
            chunk.append("Tags: ").append(String.join(", ", tags)).append("\n");
        }
        
        if (author != null && !author.isEmpty()) {
            chunk.append("Author: ").append(author).append("\n");
        }
        
        chunk.append("Path: ").append(path != null ? path : "");
        
        return chunk.toString().trim();
    }
}