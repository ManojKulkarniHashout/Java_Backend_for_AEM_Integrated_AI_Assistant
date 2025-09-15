package com.hashout.aem.aiassistant.service;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.search.documents.SearchClient;
import com.azure.search.documents.SearchClientBuilder;
import com.azure.search.documents.indexes.SearchIndexClient;
import com.azure.search.documents.indexes.SearchIndexClientBuilder;
import com.azure.search.documents.indexes.models.*;
import com.azure.search.documents.models.IndexDocumentsResult;
import com.azure.search.documents.models.IndexingResult;
import com.hashout.aem.aiassistant.model.AemContentItem;

import java.util.*;
import java.util.logging.Logger;

/**
 * Service for managing Azure AI Search index and documents
 */
public class SearchService {
    
    private static final Logger LOGGER = Logger.getLogger(SearchService.class.getName());
    
    private final SearchClient searchClient;
    private final SearchIndexClient indexClient;
    private final String indexName;
    
    public SearchService(String endpoint, String apiKey, String indexName) {
        this.indexName = indexName;
        
        AzureKeyCredential credential = new AzureKeyCredential(apiKey);
        
        this.searchClient = new SearchClientBuilder()
                .endpoint(endpoint)
                .credential(credential)
                .indexName(indexName)
                .buildClient();
        
        this.indexClient = new SearchIndexClientBuilder()
                .endpoint(endpoint)
                .credential(credential)
                .buildClient();
    }
    
    /**
     * Creates or updates the search index with the required schema
     */
    public void createOrUpdateIndex() {
        try {
            LOGGER.info("Creating or updating search index: " + indexName);
            
            List<SearchField> fields = Arrays.asList(
                new SearchField("id", SearchFieldDataType.STRING)
                        .setKey(true)
                        .setSearchable(false)
                        .setFilterable(false),
                
                new SearchField("path", SearchFieldDataType.STRING)
                        .setSearchable(true)
                        .setFilterable(true)
                        .setSortable(true),
                
                new SearchField("title", SearchFieldDataType.STRING)
                        .setSearchable(true)
                        .setFilterable(true)
                        .setSortable(true),
                
                new SearchField("description", SearchFieldDataType.STRING)
                        .setSearchable(true),
                
                new SearchField("content", SearchFieldDataType.STRING)
                        .setSearchable(true),
                
                new SearchField("textChunk", SearchFieldDataType.STRING)
                        .setSearchable(true),
                
                new SearchField("contentVector", SearchFieldDataType.collection(SearchFieldDataType.SINGLE))
                        .setSearchable(true)
                        .setVectorSearchDimensions(1536)
                        .setVectorSearchProfileName("default-vector-profile"),
                
                new SearchField("lastModified", SearchFieldDataType.STRING)
                        .setFilterable(true)
                        .setSortable(true),
                
                new SearchField("tags", SearchFieldDataType.collection(SearchFieldDataType.STRING))
                        .setSearchable(true)
                        .setFilterable(true),
                
                new SearchField("author", SearchFieldDataType.STRING)
                        .setSearchable(true)
                        .setFilterable(true),
                
                new SearchField("contentType", SearchFieldDataType.STRING)
                        .setFilterable(true)
            );
            
            // Configure vector search
            VectorSearch vectorSearch = new VectorSearch();
            vectorSearch.setProfiles(Arrays.asList(
                new VectorSearchProfile("default-vector-profile", "default-algorithm")
            ));
            vectorSearch.setAlgorithms(Arrays.asList(
                new HnswAlgorithmConfiguration("default-algorithm")
            ));
            
            SearchIndex index = new SearchIndex(indexName, fields);
            index.setVectorSearch(vectorSearch);
            
            indexClient.createOrUpdateIndex(index);
            LOGGER.info("Index created or updated successfully");
            
        } catch (Exception e) {
            LOGGER.severe("Failed to create or update index: " + e.getMessage());
            throw new RuntimeException("Failed to create or update index", e);
        }
    }
    
    /**
     * Indexes AEM content items with their embeddings
     */
    public void indexDocuments(List<AemContentItem> contentItems, List<List<Double>> embeddings) {
        try {
            if (contentItems.size() != embeddings.size()) {
                throw new IllegalArgumentException("Content items and embeddings lists must have the same size");
            }
            
            LOGGER.info("Indexing " + contentItems.size() + " documents");
            
            List<Map<String, Object>> documents = new ArrayList<>();
            
            for (int i = 0; i < contentItems.size(); i++) {
                AemContentItem item = contentItems.get(i);
                List<Double> embedding = embeddings.get(i);
                
                Map<String, Object> document = new HashMap<>();
                document.put("id", generateDocumentId(item.getPath()));
                document.put("path", item.getPath());
                document.put("title", item.getTitle());
                document.put("description", item.getDescription());
                document.put("content", item.getContent());
                document.put("textChunk", item.createTextChunk());
                document.put("contentVector", embedding);
                document.put("lastModified", item.getLastModified());
                document.put("tags", item.getTags() != null ? Arrays.asList(item.getTags()) : new ArrayList<>());
                document.put("author", item.getAuthor());
                document.put("contentType", item.getContentType());
                
                documents.add(document);
            }
            
            IndexDocumentsResult result = searchClient.uploadDocuments(documents);
            
            int successCount = 0;
            int errorCount = 0;
            
            for (IndexingResult indexingResult : result.getResults()) {
                if (indexingResult.isSucceeded()) {
                    successCount++;
                } else {
                    errorCount++;
                    LOGGER.warning("Failed to index document " + indexingResult.getKey() + 
                                   ": " + indexingResult.getErrorMessage());
                }
            }
            
            LOGGER.info("Indexing completed. Success: " + successCount + ", Errors: " + errorCount);
            
        } catch (Exception e) {
            LOGGER.severe("Failed to index documents: " + e.getMessage());
            throw new RuntimeException("Failed to index documents", e);
        }
    }
    
    /**
     * Generates a unique document ID from the AEM path
     */
    private String generateDocumentId(String path) {
        if (path == null || path.isEmpty()) {
            return UUID.randomUUID().toString();
        }
        
        // Replace special characters that might cause issues in search index
        return path.replaceAll("[^a-zA-Z0-9-_.]", "_");
    }
}