
package com.hashout.aem.aiassistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;


public class RagService {
        private final String openAIEndpoint;
        private final String openAIKey;
        private final String embeddingDeployment;
        private final String chatDeployment;
        private final ObjectMapper objectMapper = new ObjectMapper();
        // TODO: Add Azure Search REST logic if needed

        public RagService(String openAIEndpoint, String openAIKey, String embeddingDeployment, String chatDeployment) {
                this.openAIEndpoint = openAIEndpoint;
                this.openAIKey = openAIKey;
                this.embeddingDeployment = embeddingDeployment;
                this.chatDeployment = chatDeployment;
        }


    public String answerQuestion(String question) throws Exception {
        // Step 1: Create embedding for the question using REST API
        HttpClient httpClient = HttpClient.newHttpClient();
                if (openAIEndpoint == null || !openAIEndpoint.startsWith("http")) {
                        throw new IllegalArgumentException("Invalid or missing AZURE_OPENAI_ENDPOINT; must start with http(s)://");
                }
                String embeddingUrl = String.format("%s/openai/deployments/%s/embeddings?api-version=2023-05-15", openAIEndpoint, embeddingDeployment);
        String embeddingPayload = objectMapper.writeValueAsString(Map.of("input", List.of(question)));
        HttpRequest embeddingRequest = HttpRequest.newBuilder()
                .uri(URI.create(embeddingUrl))
                .header("Content-Type", "application/json")
                .header("api-key", openAIKey)
                .POST(HttpRequest.BodyPublishers.ofString(embeddingPayload))
                .build();
        HttpResponse<String> embeddingResponse = httpClient.send(embeddingRequest, HttpResponse.BodyHandlers.ofString());
        JsonNode embeddingJson = objectMapper.readTree(embeddingResponse.body());
        List<Float> embedding = new ArrayList<>();
        for (JsonNode value : embeddingJson.get("data").get(0).get("embedding")) {
            embedding.add((float) value.asDouble());
        }

        // Step 2: Query Azure AI Search with the embedding (SDK logic removed, add REST if needed)
        // Query Azure Cognitive Search REST API using the embedding
        // NOTE: You may need to configure these values appropriately
        String searchEndpoint = System.getenv("AZURE_SEARCH_ENDPOINT");
        String searchKey = System.getenv("AZURE_SEARCH_KEY");
        String searchIndex = System.getenv("AZURE_SEARCH_INDEX");
        if (searchEndpoint == null || searchKey == null || searchIndex == null) {
            throw new IllegalArgumentException("Missing Azure Search configuration (endpoint, key, or index)");
        }
        String searchUrl = String.format("%s/indexes/%s/docs/search?api-version=2023-11-01", searchEndpoint, searchIndex);
        // Construct the search payload using vector search
        Map<String, Object> searchPayloadMap = new HashMap<>();
        searchPayloadMap.put("vector", Map.of(
            "value", embedding,
            "fields", List.of("embedding"),
            "k", 3 // number of top results
        ));
        searchPayloadMap.put("top", 3);
        String searchPayload = objectMapper.writeValueAsString(searchPayloadMap);
        HttpRequest searchRequest = HttpRequest.newBuilder()
            .uri(URI.create(searchUrl))
            .header("Content-Type", "application/json")
            .header("api-key", searchKey)
            .POST(HttpRequest.BodyPublishers.ofString(searchPayload))
            .build();
        HttpResponse<String> searchResponse = httpClient.send(searchRequest, HttpResponse.BodyHandlers.ofString());
        JsonNode searchJson = objectMapper.readTree(searchResponse.body());
        // Extract context from search results
        StringBuilder contextBuilder = new StringBuilder();
        if (searchJson.has("value")) {
            for (JsonNode doc : searchJson.get("value")) {
                if (doc.has("content")) {
                    contextBuilder.append(doc.get("content").asText()).append("\n");
                }
            }
        }
        String context = contextBuilder.toString().trim();
        if (context.isEmpty()) {
            context = "No relevant context found.";
        }
        // Step 3: Send context and question to OpenAI chat model using REST API
        String chatUrl = String.format("%s/openai/deployments/%s/chat/completions?api-version=2023-05-15", openAIEndpoint, chatDeployment);
        List<Map<String, String>> messages = Arrays.asList(
                Map.of("role", "system", "content", "You are an assistant for AEM. Use the provided context to answer the question."),
                Map.of("role", "user", "content", "Context: " + context + "\nQuestion: " + question)
        );
        String chatPayload = objectMapper.writeValueAsString(Map.of("messages", messages));
        HttpRequest chatRequest = HttpRequest.newBuilder()
                .uri(URI.create(chatUrl))
                .header("Content-Type", "application/json")
                .header("api-key", openAIKey)
                .POST(HttpRequest.BodyPublishers.ofString(chatPayload))
                .build();
        HttpResponse<String> chatResponse = httpClient.send(chatRequest, HttpResponse.BodyHandlers.ofString());
        JsonNode chatJson = objectMapper.readTree(chatResponse.body());
        String answer = chatJson.get("choices").get(0).get("message").get("content").asText();
        return answer;
    }
}
