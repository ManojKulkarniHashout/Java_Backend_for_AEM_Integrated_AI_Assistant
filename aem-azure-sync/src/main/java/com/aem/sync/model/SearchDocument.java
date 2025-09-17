package com.aem.sync.model;

import java.util.List;
import java.util.Arrays;
import java.util.UUID;

public class SearchDocument {
    // Unique id per chunk (guid) or could be derived from path + index
    public String id;
    // Text chunk content
    public String chunk;
    // Vector embedding
    public List<Float> vector;
    // Core metadata fields flattened for filtering/faceting
    public String path;
    public String title;
    public String template;
    public String lastModified;
    public String lastModifiedBy;
    public String published;
    public String publishedBy;
    public List<String> tags; // convertible from array
    public String workflowState;

    public SearchDocument(String chunk, List<Float> vector, AemContent source, String idOverride) {
        this.id = (idOverride != null ? idOverride : UUID.randomUUID().toString());
        this.chunk = chunk;
        this.vector = vector;
        if (source != null) {
            this.path = source.path;
            this.title = source.title;
            this.template = source.template;
            this.lastModified = source.lastModified;
            this.lastModifiedBy = source.lastModifiedBy;
            this.published = source.published;
            this.publishedBy = source.publishedBy;
            this.workflowState = source.workflowState;
            if (source.tags != null) {
                this.tags = Arrays.asList(source.tags);
            }
        }
    }
}
