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
import java.util.*;
import com.aem.sync.model.AemContent;
import com.aem.sync.model.SearchDocument;

public class SyncAemContentService {
    public static void sync(ExecutionContext context) {
        try {
            String aemUrl = System.getenv("AEM_EXPORT_SERVLET_URL");
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url(aemUrl).build();
            Response response = client.newCall(request).execute();
            if (!response.isSuccessful()) throw new RuntimeException("Failed to fetch AEM data");
            String json = response.body().string();
            ObjectMapper mapper = new ObjectMapper();
            List<AemContent> contents = Arrays.asList(mapper.readValue(json, AemContent[].class));
            List<SearchDocument> docs = new ArrayList<>();
            OpenAIClient openAIClient = new OpenAIClientBuilder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .credential(new AzureKeyCredential(System.getenv("AZURE_OPENAI_API_KEY")))
                .buildClient();
            String deployment = System.getenv("AZURE_OPENAI_EMBEDDING_DEPLOYMENT");
            for (AemContent content : contents) {
                String chunk = content.toChunk();
                EmbeddingsOptions options = new EmbeddingsOptions(Arrays.asList(chunk));
                Embeddings embeddings = openAIClient.getEmbeddings(deployment, options);
                // Convert List<Double> to List<Float>
                List<Double> doubleVector = embeddings.getData().get(0).getEmbedding();
                List<Float> vector = new ArrayList<>();
                for (Double d : doubleVector) {
                    vector.add(d.floatValue());
                }
                SearchDocument doc = new SearchDocument(chunk, vector, content);
                docs.add(doc);
            }
            SearchClient searchClient = new SearchClientBuilder()
                .endpoint(System.getenv("AZURE_SEARCH_ENDPOINT"))
                .credential(new AzureKeyCredential(System.getenv("AZURE_SEARCH_API_KEY")))
                .indexName("aem-content-index")
                .buildClient();
            searchClient.uploadDocuments(docs);
            context.getLogger().info("Uploaded " + docs.size() + " documents to Azure Search.");
        } catch (Exception e) {
            context.getLogger().severe("Sync failed: " + e.getMessage());
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
