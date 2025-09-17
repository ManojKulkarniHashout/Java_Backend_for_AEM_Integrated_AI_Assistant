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
        context.getLogger().info("=== AEM Sync Pipeline Starting ===");
        try {
            // Phase 1: Fetch
            context.getLogger().info("Phase 1: AEM Data Fetch");
            String aemUrl = System.getenv("AEM_EXPORT_SERVLET_URL");
            if (aemUrl == null || aemUrl.isBlank()) throw new IllegalStateException("AEM_EXPORT_SERVLET_URL env var is required");
            String rawText = fetchAemWithRetry(aemUrl, context);
            context.getLogger().info("Fetched chars: " + rawText.length());

            // Phase 2: Chunk
            context.getLogger().info("Phase 2: Content Chunking");
            List<String> chunks = chunkTextWithOpenAIWithRetry(rawText, context);
            context.getLogger().info("Chunk count: " + chunks.size());

            // Phase 3: Embeddings
            context.getLogger().info("Phase 3: Embedding Generation");
            List<SearchDocument> docs = new ArrayList<>();
            String openAiKey = coalesce(System.getenv("AZURE_OPENAI_API_KEY"), System.getenv("AZURE_OPENAI_KEY"));
            if (openAiKey == null || openAiKey.isBlank()) throw new IllegalStateException("AZURE_OPENAI_API_KEY or AZURE_OPENAI_KEY must be set for embeddings");
            OpenAIClient openAIClient = new OpenAIClientBuilder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .credential(new AzureKeyCredential(openAiKey))
                .buildClient();
            String embeddingDeployment = System.getenv("AZURE_OPENAI_EMBEDDING_DEPLOYMENT");
            if (embeddingDeployment == null || embeddingDeployment.isBlank()) throw new IllegalStateException("AZURE_OPENAI_EMBEDDING_DEPLOYMENT env var is required");

            int maxEmbedRetries = intFromEnv("SYNC_MAX_EMBED_RETRIES", 2);
            long embedBackoffMs = longFromEnv("SYNC_EMBED_BACKOFF_MS", 600);
            int idx = 0;
            for (String chunk : chunks) {
                context.getLogger().info("Embedding chunk " + (idx + 1) + "/" + chunks.size());
                List<Float> vector = null;
                int attempt = 0;
                while (attempt <= maxEmbedRetries) {
                    try {
                        EmbeddingsOptions opts = new EmbeddingsOptions(Arrays.asList(chunk));
                        Embeddings emb = openAIClient.getEmbeddings(embeddingDeployment, opts);
                        vector = emb.getData().get(0).getEmbedding();
                        break;
                    } catch (Exception ex) {
                        attempt++;
                        if (attempt > maxEmbedRetries) {
                            context.getLogger().severe("Embedding failed after attempts=" + attempt + ": " + ex.getMessage());
                        } else {
                            context.getLogger().warning("Embedding attempt " + attempt + " failed: " + ex.getMessage() + " retry in " + embedBackoffMs + "ms");
                            try { Thread.sleep(embedBackoffMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                        }
                    }
                }
                if (vector != null) {
                    // Placeholder for associating original metadata if per-item chunk mapping becomes available
                    String syntheticId = "chunk-" + idx + "-" + System.currentTimeMillis();
                    docs.add(new SearchDocument(chunk, vector, null, syntheticId));
                }
                idx++;
            }
            context.getLogger().info("Embeddings created for " + docs.size() + " chunks (" + chunks.size() + " original).");

            // Phase 4: Index
            String searchEndpoint = System.getenv("AZURE_SEARCH_ENDPOINT");
            String searchKey = System.getenv("AZURE_SEARCH_API_KEY");
            if (searchEndpoint == null || searchKey == null) {
                context.getLogger().warning("Search configuration missing; skipping upload.");
                context.getLogger().info("=== AEM Sync Pipeline Complete (No Search Upload) ===");
                return;
            }
            SearchClient searchClient = new SearchClientBuilder()
                .endpoint(searchEndpoint)
                .credential(new AzureKeyCredential(searchKey))
                .indexName("aem-content-index")
                .buildClient();
            context.getLogger().info("Uploading " + docs.size() + " documents to Azure Search...");
            searchClient.uploadDocuments(docs);
            context.getLogger().info("Upload complete.");
            context.getLogger().info("=== AEM Sync Pipeline Complete Successfully ===");
        } catch (Exception e) {
            context.getLogger().severe("Pipeline failed: " + e.getMessage());
            throw new RuntimeException("AEM Sync Pipeline failed", e);
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

    // Retry wrapper for chunking
    private static List<String> chunkTextWithOpenAIWithRetry(String rawText, ExecutionContext context) {
        int maxRetries = intFromEnv("SYNC_MAX_OPENAI_RETRIES", 3);
        long backoffMs = longFromEnv("SYNC_OPENAI_BACKOFF_MS", 750);
        int attempt = 0;
        while (true) {
            try {
                return chunkTextWithOpenAI(rawText, context);
            } catch (Exception ex) {
                attempt++;
                if (attempt > maxRetries) {
                    context.getLogger().severe("OpenAI chunking permanently failed after " + attempt + " attempts: " + ex.getMessage());
                    return Arrays.asList(rawText.split("\n\n"));
                } else {
                    context.getLogger().warning("OpenAI chunking attempt " + attempt + " failed: " + ex.getMessage() + "; retrying in " + backoffMs + "ms");
                    try { Thread.sleep(backoffMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return Arrays.asList(rawText.split("\n\n")); }
                }
            }
        }
    }

    private static String fetchAemWithRetry(String url, ExecutionContext context) throws Exception {
        int maxRetries = intFromEnv("SYNC_MAX_AEM_RETRIES", 3);
        long backoffMs = longFromEnv("SYNC_AEM_BACKOFF_MS", 500);
        OkHttpClient client = new OkHttpClient();
        String aemUser = System.getenv("AEM_USERNAME");
        String aemPass = System.getenv("AEM_PASSWORD");
        String aemToken = System.getenv("AEM_AUTH_TOKEN");
        int attempt = 0;
        while (true) {
            attempt++;
            Request.Builder builder = new Request.Builder().url(url);
            if (aemToken != null && !aemToken.isBlank()) {
                builder.addHeader("Authorization", "Bearer " + aemToken);
            } else if (aemUser != null && !aemUser.isBlank() && aemPass != null) {
                String basic = java.util.Base64.getEncoder().encodeToString((aemUser + ":" + aemPass).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                builder.addHeader("Authorization", "Basic " + basic);
            }
            Request request = builder.build();
            try (Response response = client.newCall(request).execute()) {
                context.getLogger().info("AEM response status: " + response.code());
                if (!response.isSuccessful()) {
                    if (response.code() >= 500 && attempt <= maxRetries) {
                        context.getLogger().warning("AEM fetch server error (" + response.code() + ") attempt " + attempt + " - retrying in " + backoffMs + "ms");
                        Thread.sleep(backoffMs);
                        continue;
                    }
                    throw new RuntimeException("Failed to fetch AEM data (status " + response.code() + ")");
                }
                return response.body().string();
            } catch (Exception ex) {
                if (attempt > maxRetries) {
                    throw ex;
                }
                context.getLogger().warning("AEM fetch attempt " + attempt + " failed: " + ex.getMessage() + " - retrying in " + backoffMs + "ms");
                try { Thread.sleep(backoffMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); throw ex; }
            }
        }
    }

    private static int intFromEnv(String key, int defVal) {
        try {
            String v = System.getenv(key);
            return (v == null || v.isBlank()) ? defVal : Integer.parseInt(v.trim());
        } catch (Exception e) { return defVal; }
    }
    private static long longFromEnv(String key, long defVal) {
        try {
            String v = System.getenv(key);
            return (v == null || v.isBlank()) ? defVal : Long.parseLong(v.trim());
        } catch (Exception e) { return defVal; }
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
