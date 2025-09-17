package com.aem.sync.service;

import com.microsoft.azure.functions.ExecutionContext;
import okhttp3.*;
import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.*;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.search.documents.SearchClient;
import com.azure.search.documents.SearchClientBuilder;
import com.azure.search.documents.models.IndexDocumentsResult;
// Removed unused imports for batch upload
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import com.aem.sync.model.AemContent;
import com.aem.sync.model.SearchDocument;

public class SyncAemContentService {
    public static void sync(ExecutionContext context) {
        try {
            context.getLogger().info("=== AEM Sync Pipeline Starting ===");
            context.getLogger().info("Phase 1: AEM Data Fetch");
            
            String aemUrl = System.getenv("AEM_EXPORT_SERVLET_URL");
            if (aemUrl == null || aemUrl.isBlank()) {
                throw new IllegalStateException("AEM_EXPORT_SERVLET_URL env var is required");
            }
            context.getLogger().info("AEM Export URL: " + aemUrl);
            okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
            okhttp3.Request.Builder reqBuilder = new okhttp3.Request.Builder().url(aemUrl);
            // Optional AEM auth: Basic (AEM_USERNAME/AEM_PASSWORD) or Bearer (AEM_AUTH_TOKEN)
            String aemUser = System.getenv("AEM_USERNAME");
            String aemPass = System.getenv("AEM_PASSWORD");
            String aemToken = System.getenv("AEM_AUTH_TOKEN");
            
            String authType = "none";
            if (aemToken != null && !aemToken.isBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer " + aemToken);
                authType = "Bearer token";
            } else if (aemUser != null && !aemUser.isBlank() && aemPass != null) {
                String basic = java.util.Base64.getEncoder().encodeToString((aemUser + ":" + aemPass).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                reqBuilder.addHeader("Authorization", "Basic " + basic);
                authType = "Basic auth";
            }
            context.getLogger().info("Authentication method: " + authType);
            okhttp3.Request request = reqBuilder.build();
            okhttp3.Response response = client.newCall(request).execute();
            context.getLogger().info("AEM response status: " + response.code());
            if (!response.isSuccessful()) {
                String errorMsg = "Failed to fetch AEM data - HTTP " + response.code() + ": " + response.message();
                context.getLogger().severe(errorMsg);
                throw new RuntimeException(errorMsg);
            }
            String rawText = response.body().string();
            context.getLogger().info("Successfully fetched " + rawText.length() + " chars from AEM");
            
            context.getLogger().info("Phase 2: Content Chunking");
            java.util.List<String> chunks = chunkTextWithOpenAI(rawText, context);
            context.getLogger().info("Chunking complete. Generated " + chunks.size() + " chunks");
            
            context.getLogger().info("Phase 3: Embedding Generation");
            java.util.List<com.aem.sync.model.SearchDocument> docs = new java.util.ArrayList<>();

            String openAiKey = coalesce(System.getenv("AZURE_OPENAI_API_KEY"), System.getenv("AZURE_OPENAI_KEY"));
            if (openAiKey == null || openAiKey.isBlank()) {
                throw new IllegalStateException("AZURE_OPENAI_API_KEY or AZURE_OPENAI_KEY must be set for embeddings");
            }
            context.getLogger().info("OpenAI API key configured: " + (openAiKey.length() > 8 ? openAiKey.substring(0, 8) + "..." : "***"));

            com.azure.ai.openai.OpenAIClient openAIClient = new com.azure.ai.openai.OpenAIClientBuilder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .credential(new com.azure.core.credential.AzureKeyCredential(openAiKey))
                .buildClient();
            String deployment = System.getenv("AZURE_OPENAI_EMBEDDING_DEPLOYMENT");
            if (deployment == null || deployment.isBlank()) {
                throw new IllegalStateException("AZURE_OPENAI_EMBEDDING_DEPLOYMENT env var is required");
            }
            context.getLogger().info("Embedding deployment: " + deployment);
            
            int chunkNum = 1;
            for (String chunk : chunks) {
                if (chunkNum % 5 == 0 || chunkNum == 1 || chunkNum == chunks.size()) {
                    context.getLogger().info("Processing chunk " + chunkNum + "/" + chunks.size() + " (length: " + chunk.length() + ")");
                }
                com.azure.ai.openai.models.EmbeddingsOptions options = new com.azure.ai.openai.models.EmbeddingsOptions(java.util.Arrays.asList(chunk));
                com.azure.ai.openai.models.Embeddings embeddings = openAIClient.getEmbeddings(deployment, options);
                java.util.List<Float> vector = embeddings.getData().get(0).getEmbedding();
                com.aem.sync.model.SearchDocument doc = new com.aem.sync.model.SearchDocument(chunk, vector, null);
                docs.add(doc);
                chunkNum++;
            }
            context.getLogger().info("Successfully generated embeddings for all " + chunks.size() + " chunks");

            context.getLogger().info("Phase 4: Azure Search Upload");

            String searchEndpoint = System.getenv("AZURE_SEARCH_ENDPOINT");
            String searchKey = System.getenv("AZURE_SEARCH_API_KEY");
            if (searchEndpoint == null || searchKey == null) {
                context.getLogger().warning("Azure Search endpoint/key missing; skipping search upload");
                context.getLogger().info("=== AEM Sync Pipeline Complete (without search upload) ===");
                return;
            }
            context.getLogger().info("Search endpoint: " + searchEndpoint);
            context.getLogger().info("Search index: aem-content-index");
            
            com.azure.search.documents.SearchClient searchClient = new com.azure.search.documents.SearchClientBuilder()
                .endpoint(searchEndpoint)
                .credential(new com.azure.core.credential.AzureKeyCredential(searchKey))
                .indexName("aem-content-index")
                .buildClient();
            context.getLogger().info("Uploading " + docs.size() + " documents to Azure Search...");
            searchClient.uploadDocuments(docs);
            context.getLogger().info("Successfully uploaded " + docs.size() + " documents to Azure Search");
            context.getLogger().info("=== AEM Sync Pipeline Complete Successfully ===");
        } catch (Exception e) {
            context.getLogger().severe("=== AEM Sync Pipeline Failed ===");
            context.getLogger().severe("Error: " + e.getMessage());
            if (e.getCause() != null) {
                context.getLogger().severe("Caused by: " + e.getCause().getMessage());
            }
            throw new RuntimeException("AEM Sync Pipeline failed: " + e.getMessage(), e);
        }
    }

    private static String coalesce(String a, String b) { return (a != null && !a.isBlank()) ? a : b; }

    // Use OpenAI to split and summarize text into chunks
    private static List<String> chunkTextWithOpenAI(String rawText, ExecutionContext context) {
        context.getLogger().info("Attempting OpenAI-powered intelligent chunking...");
        String endpoint = System.getenv("AZURE_OPENAI_ENDPOINT");
        String apiKey = System.getenv("AZURE_OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getenv("AZURE_OPENAI_KEY");
        }
        String deployment = System.getenv("AZURE_OPENAI_CHAT_DEPLOYMENT");
        String apiVersion = Optional.ofNullable(System.getenv("AZURE_OPENAI_API_VERSION")).orElse("2024-02-15-preview");

        if (endpoint == null || apiKey == null || deployment == null) {
            context.getLogger().warning("Missing OpenAI config (endpoint/key/deployment). Falling back to naive paragraph split");
            List<String> fallbackChunks = Arrays.asList(rawText.split("\n\n"));
            context.getLogger().info("Fallback chunking created " + fallbackChunks.size() + " chunks");
            return fallbackChunks;
        }
        
        context.getLogger().info("Using OpenAI Chat deployment: " + deployment);

        try {
            OkHttpClient http = new OkHttpClient();
            String url = String.format(Locale.ROOT, "%s/openai/deployments/%s/chat/completions?api-version=%s", endpoint, deployment, apiVersion);
            String systemPrompt = "You are an expert document segmenter. Break the user's large document into concise, self-contained chunks (max ~500 tokens each) for semantic search. Output ONLY the chunks, one per line, no numbering.";
            String userPrompt = "Document to segment:\n\n" + rawText;

            Map<String, Object> body = new HashMap<>();
            body.put("messages", Arrays.asList(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
            ));
            body.put("temperature", 0.2);
            body.put("max_tokens", 2048);

            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(body);
            Request request = new Request.Builder()
                .url(url)
                .addHeader("api-key", apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .build();

            try (Response response = http.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    context.getLogger().warning("OpenAI chunking request failed: HTTP " + response.code() + " - " + response.message());
                    throw new RuntimeException("Chat completion failed: HTTP " + response.code() + " - " + response.message());
                }
                String responseBody = response.body().string();
                JsonNode root = mapper.readTree(responseBody);
                JsonNode choices = root.path("choices");
                if (!choices.isArray() || choices.size() == 0) {
                    throw new RuntimeException("No choices returned from OpenAI");
                }
                String content = choices.get(0).path("message").path("content").asText("");
                if (content.isEmpty()) {
                    throw new RuntimeException("Empty content returned from OpenAI");
                }
                List<String> chunks = new ArrayList<>();
                for (String line : content.split("\n")) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        chunks.add(trimmed);
                    }
                }
                context.getLogger().info("OpenAI intelligent chunking successful: " + chunks.size() + " semantic chunks created");
                return chunks;
            }
        } catch (Exception e) {
            context.getLogger().warning("OpenAI chunking failed: " + e.getMessage() + ". Falling back to naive paragraph split");
            List<String> fallbackChunks = Arrays.asList(rawText.split("\n\n"));
            context.getLogger().info("Fallback chunking created " + fallbackChunks.size() + " chunks");
            return fallbackChunks;
        }
    }

    // For local testing: read sample JSON file
    public static void syncFromFile(String filePath, ExecutionContext context) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            List<AemContent> contents = Arrays.asList(mapper.readValue(new java.io.File(filePath), AemContent[].class));
            List<SearchDocument> docs = new ArrayList<>();
            // You can mock OpenAIClient and SearchClient here for local testing, or just print chunks
            for (AemContent content : contents) {
                String chunk = content.toChunk();
                context.getLogger().info("Chunk: " + chunk);
            }
            context.getLogger().info("Parsed " + contents.size() + " AEM content objects from file: " + filePath);
        } catch (Exception e) {
            context.getLogger().severe("Local file sync failed: " + e.getMessage());
        }
    }
}
