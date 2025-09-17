package com.hashout.aem.aiassistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hashout.aem.aiassistant.model.AemContentItem;
import com.hashout.aem.aiassistant.model.ChatRequest;
import com.hashout.aem.aiassistant.model.ChatResponse;
import com.hashout.aem.aiassistant.model.SearchResult;
import com.hashout.aem.aiassistant.service.AemDataService;
import com.hashout.aem.aiassistant.service.ChatService;
import com.hashout.aem.aiassistant.service.EmbeddingService;
import com.hashout.aem.aiassistant.service.SearchService;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Azure Functions for AEM AI Assistant
 */
public class Function {
    
    private static final Logger LOGGER = Logger.getLogger(Function.class.getName());
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Timer-triggered function that syncs AEM content to Azure AI Search
     * Runs on schedule (default: every hour) as defined in local.settings.json
     */
//    @FunctionName("SyncAemContent")
//    public void syncAemContent(
//            @TimerTrigger(name = "timerInfo", schedule = "%SYNC_SCHEDULE%") String timerInfo,
//            final ExecutionContext context) {
//        Logger logger = context.getLogger();
//        logger.info("SyncAemContent timer trigger function executed at: " + LocalDateTime.now());
//        logger.info("Timer Info: " + timerInfo);
//        try {
//            performSync(logger);
//            logger.info("AEM content sync completed successfully");
//        } catch (Exception e) {
//            logger.severe("Failed to sync AEM content: " + e.getMessage());
//            throw new RuntimeException("Sync failed", e);
//        }
//    }
    
    /**
     * HTTP-triggered function for on-demand AEM content sync
     * Provides manual trigger capability for the sync process
     */
    @FunctionName("SyncAemContentOnDemand")
    public HttpResponseMessage syncAemContentOnDemand(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.POST},
                authLevel = AuthorizationLevel.FUNCTION)
                HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        
        Logger logger = context.getLogger();
        logger.info("On-demand AEM content sync requested");
        
        try {
            performSync(logger);
            
            return request.createResponseBuilder(HttpStatus.OK)
                    .body("AEM content sync completed successfully at " + LocalDateTime.now())
                    .build();
                    
        } catch (Exception e) {
            logger.severe("Failed to sync AEM content: " + e.getMessage());
            
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Sync failed: " + e.getMessage())
                    .build();
        }
    }
    
    /**
     * HTTP-triggered function for RAG-based chat - AskQuestion endpoint
     */
    @FunctionName("AskQuestion")
    public HttpResponseMessage askQuestion(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.POST},
                authLevel = AuthorizationLevel.FUNCTION)
                HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        
        Logger logger = context.getLogger();
        logger.info("AskQuestion endpoint called");
        
        try {
            // Parse the request body
            String requestBody = request.getBody().orElse("");
            if (requestBody.isEmpty()) {
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                        .body("{\"error\": \"Request body is required\"}")
                        .header("Content-Type", "application/json")
                        .build();
            }
            
            ChatRequest chatRequest;
            try {
                chatRequest = objectMapper.readValue(requestBody, ChatRequest.class);
            } catch (Exception e) {
                logger.warning("Failed to parse request JSON: " + e.getMessage());
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                        .body("{\"error\": \"Invalid JSON format\"}")
                        .header("Content-Type", "application/json")
                        .build();
            }
            
            // Validate the request
            if (!chatRequest.isValid()) {
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                        .body("{\"error\": \"Question is required and cannot be empty\"}")
                        .header("Content-Type", "application/json")
                        .build();
            }
            
            String userQuestion = chatRequest.getQuestion();
            logger.info("Processing question: " + userQuestion.substring(0, Math.min(100, userQuestion.length())));
            
            // Get configuration from environment variables
            String openAIEndpoint = System.getenv("AZURE_OPENAI_ENDPOINT");
            String openAIApiKey = System.getenv("AZURE_OPENAI_API_KEY");
            String openAIEmbeddingDeployment = System.getenv("AZURE_OPENAI_DEPLOYMENT_NAME");
            String openAIChatDeployment = System.getenv("AZURE_OPENAI_CHAT_DEPLOYMENT_NAME");
            
            String searchEndpoint = System.getenv("AZURE_SEARCH_ENDPOINT");
            String searchApiKey = System.getenv("AZURE_SEARCH_API_KEY");
            String searchIndexName = System.getenv("AZURE_SEARCH_INDEX_NAME");
            
            // Validate configuration
            validateConfiguration(openAIEndpoint, openAIApiKey, openAIEmbeddingDeployment, 
                                  searchEndpoint, searchApiKey, searchIndexName);
            
            // If chat deployment is not configured, use the embedding deployment
            if (openAIChatDeployment == null || openAIChatDeployment.trim().isEmpty()) {
                openAIChatDeployment = openAIEmbeddingDeployment;
                logger.info("Using embedding deployment for chat: " + openAIChatDeployment);
            }
            
            // Initialize services
            EmbeddingService embeddingService = new EmbeddingService(openAIEndpoint, openAIApiKey, openAIEmbeddingDeployment);
            SearchService searchService = new SearchService(searchEndpoint, searchApiKey, searchIndexName);
            ChatService chatService = new ChatService(openAIEndpoint, openAIApiKey, openAIChatDeployment);
            
            try {
                // Step 1: Generate embedding for the user question
                logger.info("Step 1: Generating embedding for user question");
                List<Double> questionEmbedding = embeddingService.generateEmbedding(userQuestion);
                
                // Step 2: Perform vector search to find relevant context
                logger.info("Step 2: Performing vector search for relevant context");
                List<SearchResult> searchResults = searchService.vectorSearch(questionEmbedding, 5);
                
                // Step 3: Extract context from search results
                logger.info("Step 3: Extracting context from search results");
                List<String> contextChunks = searchService.extractContextFromResults(searchResults);
                
                // Step 4: Generate chat response using context
                logger.info("Step 4: Generating chat response");
                String answer = chatService.generateChatResponse(userQuestion, contextChunks);
                
                // Step 5: Build response with sources
                List<String> sources = new ArrayList<>();
                for (SearchResult result : searchResults) {
                    if (result.getPath() != null && !result.getPath().isEmpty()) {
                        sources.add(result.getPath());
                    }
                }
                
                ChatResponse chatResponse = new ChatResponse(
                    answer,
                    sources,
                    searchResults.isEmpty() ? "low" : "medium",
                    LocalDateTime.now().toString()
                );
                
                String responseJson = objectMapper.writeValueAsString(chatResponse);
                
                logger.info("Successfully generated chat response");
                return request.createResponseBuilder(HttpStatus.OK)
                        .body(responseJson)
                        .header("Content-Type", "application/json")
                        .build();
                        
            } catch (Exception e) {
                logger.severe("Error during RAG processing: " + e.getMessage());
                return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("{\"error\": \"Failed to process question: " + e.getMessage() + "\"}")
                        .header("Content-Type", "application/json")
                        .build();
            }
            
        } catch (Exception e) {
            logger.severe("Failed to process AskQuestion request: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"Internal server error\"}")
                    .header("Content-Type", "application/json")
                    .build();
        }
    }
    
    /**
     * Performs the actual sync operation
     */
    private void performSync(Logger logger) {
        try {
            // Get configuration from environment variables
            String aemDataExportUrl = System.getenv("AEM_DATA_EXPORT_URL");
            String aemUsername = System.getenv("AEM_USERNAME");
            String aemPassword = System.getenv("AEM_PASSWORD");
            
            String openAIEndpoint = System.getenv("AZURE_OPENAI_ENDPOINT");
            String openAIApiKey = System.getenv("AZURE_OPENAI_API_KEY");
            String openAIDeploymentName = System.getenv("AZURE_OPENAI_DEPLOYMENT_NAME");
            
            String searchEndpoint = System.getenv("AZURE_SEARCH_ENDPOINT");
            String searchApiKey = System.getenv("AZURE_SEARCH_API_KEY");
            String searchIndexName = System.getenv("AZURE_SEARCH_INDEX_NAME");
            
            // Validate configuration
            validateConfiguration(aemDataExportUrl, aemUsername, aemPassword,
                                  openAIEndpoint, openAIApiKey, openAIDeploymentName,
                                  searchEndpoint, searchApiKey, searchIndexName);
            
            logger.info("Configuration validated, starting sync process");
            
            // Initialize services
            AemDataService aemDataService = new AemDataService(aemDataExportUrl, aemUsername, aemPassword);
            EmbeddingService embeddingService = new EmbeddingService(openAIEndpoint, openAIApiKey, openAIDeploymentName);
            SearchService searchService = new SearchService(searchEndpoint, searchApiKey, searchIndexName);
            
            try {
                // Step 1: Fetch AEM content
                logger.info("Step 1: Fetching AEM content");
                List<AemContentItem> contentItems = aemDataService.fetchAemContent();
                
                if (contentItems.isEmpty()) {
                    logger.warning("No content items found in AEM");
                    return;
                }
                
                // Step 2: Create or update search index
                logger.info("Step 2: Creating or updating search index");
                searchService.createOrUpdateIndex();
                
                // Step 3: Generate embeddings for each content item
                logger.info("Step 3: Generating embeddings for " + contentItems.size() + " items");
                List<List<Double>> embeddings = new ArrayList<>();
                List<AemContentItem> validContentItems = new ArrayList<>();
                int itemIndex = 0;
                for (AemContentItem item : contentItems) {
                    String textChunk = item.createTextChunk();

                    if (textChunk != null && !textChunk.trim().isEmpty()) {
                        List<Double> embedding = embeddingService.generateEmbedding(textChunk);
                        embeddings.add(embedding);
                        validContentItems.add(item);

                        logger.info("Generated embedding for item " + (itemIndex + 1) + "/" + contentItems.size() +
                                   " (path: " + item.getPath() + ")");
                        itemIndex++;
                    } else {
                        logger.warning("Skipping empty text chunk for item: " + item.getPath());
                    }
                }
                
                // Step 4: Index documents in Azure AI Search
                logger.info("Step 4: Indexing " + validContentItems.size() + " documents in Azure AI Search");
                searchService.indexDocuments(validContentItems, embeddings);
                
                logger.info("Sync process completed successfully");
                
            } finally {
                // Clean up resources
                aemDataService.close();
            }
        } catch (Exception e) {
            logger.severe("Error during sync process: " + e.getMessage());
            throw new RuntimeException("Sync process failed", e);
        }
    }
    
    /**
     * Validates that all required configuration is present
     */
    private void validateConfiguration(String... configs) {
        for (String config : configs) {
            if (config == null || config.trim().isEmpty()) {
                throw new IllegalArgumentException("Missing required configuration. Please check local.settings.json");
            }
        }
    }
    
    /**
     * HTTP example endpoint (keeping original for reference)
     */
    @FunctionName("HttpExample")
    public HttpResponseMessage run(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.GET, HttpMethod.POST},
                authLevel = AuthorizationLevel.ANONYMOUS)
                HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        context.getLogger().info("Java HTTP trigger processed a request.");

        // Parse query parameter
        final String query = request.getQueryParameters().get("name");
        final String name = request.getBody().orElse(query);

        if (name == null) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body("Please pass a name on the query string or in the request body").build();
        } else {
            return request.createResponseBuilder(HttpStatus.OK).body("Hello, " + name).build();
        }
    }
}
