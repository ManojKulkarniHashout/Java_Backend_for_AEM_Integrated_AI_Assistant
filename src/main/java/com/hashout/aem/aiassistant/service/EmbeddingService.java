package com.hashout.aem.aiassistant.service;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.EmbeddingsOptions;
import com.azure.ai.openai.models.Embeddings;
import com.azure.core.credential.AzureKeyCredential;

import java.util.List;
import java.util.logging.Logger;

/**
 * Service for generating vector embeddings using Azure OpenAI
 */
public class EmbeddingService {
    
    private static final Logger LOGGER = Logger.getLogger(EmbeddingService.class.getName());
    
    private final OpenAIClient openAIClient;
    private final String deploymentName;
    
    public EmbeddingService(String endpoint, String apiKey, String deploymentName) {
        this.deploymentName = deploymentName;
        
        this.openAIClient = new OpenAIClientBuilder()
                .endpoint(endpoint)
                .credential(new AzureKeyCredential(apiKey))
                .buildClient();
    }
    
    /**
     * Generates vector embeddings for a text chunk
     */
    public List<Double> generateEmbedding(String text) {
        try {
            LOGGER.info("Generating embedding for text chunk (length: " + text.length() + ")");
            
            EmbeddingsOptions embeddingsOptions = new EmbeddingsOptions(List.of(text));
            Embeddings result = openAIClient.getEmbeddings(deploymentName, embeddingsOptions);
            
            if (result.getData().isEmpty()) {
                throw new RuntimeException("No embedding data received from OpenAI");
            }
            
            // Convert Float to Double for compatibility
            List<Float> floatEmbedding = result.getData().get(0).getEmbedding();
            List<Double> embedding = floatEmbedding.stream()
                    .map(Float::doubleValue)
                    .collect(java.util.stream.Collectors.toList());
            
            LOGGER.info("Successfully generated embedding with dimension: " + embedding.size());
            
            return embedding;
            
        } catch (Exception e) {
            LOGGER.severe("Failed to generate embedding: " + e.getMessage());
            throw new RuntimeException("Failed to generate embedding", e);
        }
    }
}