package com.aem.sync.model;

import java.util.List;

public class SearchDocument {
    public String chunk;
    public List<Float> vector;
    public AemContent metadata;

    public SearchDocument(String chunk, List<Float> vector, AemContent metadata) {
        this.chunk = chunk;
        this.vector = vector;
        this.metadata = metadata;
    }
}
