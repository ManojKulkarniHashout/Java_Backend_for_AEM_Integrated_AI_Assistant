package com.aem.sync.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AemContent {
    @JsonProperty("path")
    public String path;
    @JsonProperty("title")
    public String title;
    @JsonProperty("template")
    public String template;
    @JsonProperty("lastModified")
    public String lastModified;
    @JsonProperty("lastModifiedBy")
    public String lastModifiedBy;
    @JsonProperty("published")
    public String published;
    @JsonProperty("publishedBy")
    public String publishedBy;
    @JsonProperty("tags")
    public String[] tags;
    @JsonProperty("workflowState")
    public String workflowState;

    public String toChunk() {
        StringBuilder sb = new StringBuilder();
        sb.append(title != null ? title : "Untitled");
        if (tags != null && tags.length > 0) {
            sb.append(" [Tags: ").append(String.join(", ", tags)).append("]");
        }
        if (template != null) {
            sb.append(" | Template: ").append(template);
        }
        if (workflowState != null) {
            sb.append(" | Workflow: ").append(workflowState);
        }
        sb.append(" | Path: ").append(path);
        return sb.toString();
    }
}
