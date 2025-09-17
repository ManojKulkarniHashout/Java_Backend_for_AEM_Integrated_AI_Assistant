package com.hashout.aem.aiassistant;

import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.HttpMethod;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hashout.aem.aiassistant.model.AskQuestionRequest;
import com.hashout.aem.aiassistant.model.AskQuestionResponse;
import java.util.Optional;
// ...existing code...

public class AskQuestionFunction {
    @FunctionName("AskQuestion")
    public HttpResponseMessage run(
            @HttpTrigger(name = "req", methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS, route = "AskQuestion")
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        context.getLogger().info("Processing AskQuestion request");
        ObjectMapper mapper = new ObjectMapper();
        try {
            String body = request.getBody().orElse("").trim();
            if (body.isEmpty()) {
                return badRequest(request, "Empty request body. Expected JSON { \"question\": \"...\" }");
            }
            AskQuestionRequest askRequest;
            try {
                askRequest = mapper.readValue(body, AskQuestionRequest.class);
            } catch (Exception parseEx) {
                return badRequest(request, "Malformed JSON: " + sanitize(parseEx.getMessage()));
            }
            if (askRequest == null || askRequest.getQuestion() == null || askRequest.getQuestion().trim().isEmpty()) {
                return badRequest(request, "Field 'question' is required and must be non-empty.");
            }
            String question = askRequest.getQuestion().trim();

            // Load config from environment variables (with fallbacks)
            String openAIEndpoint = firstNonEmpty(
                    System.getenv("AZURE_OPENAI_ENDPOINT"),
                    System.getenv("OPENAI_ENDPOINT")
            );
            String openAIKey = firstNonEmpty(
                    System.getenv("AZURE_OPENAI_KEY"),
                    System.getenv("AZURE_OPENAI_API_KEY"),
                    System.getenv("OPENAI_API_KEY")
            );
            String embeddingDeployment = firstNonEmpty(
                    System.getenv("AZURE_OPENAI_EMBEDDING_DEPLOYMENT"),
                    System.getenv("AZURE_OPENAI_EMBEDDINGS_DEPLOYMENT")
            );
            String chatDeployment = firstNonEmpty(
                    System.getenv("AZURE_OPENAI_CHAT_DEPLOYMENT"),
                    System.getenv("AZURE_OPENAI_COMPLETIONS_DEPLOYMENT")
            );

            // Validate env vars
            StringBuilder missing = new StringBuilder();
            if (isBlank(openAIEndpoint)) missing.append("AZURE_OPENAI_ENDPOINT ");
            if (isBlank(openAIKey)) missing.append("AZURE_OPENAI_KEY ");
            if (isBlank(embeddingDeployment)) missing.append("AZURE_OPENAI_EMBEDDING_DEPLOYMENT ");
            if (isBlank(chatDeployment)) missing.append("AZURE_OPENAI_CHAT_DEPLOYMENT ");
            if (missing.length() > 0) {
                return badRequest(request, "Missing required environment variables: " + missing.toString().trim());
            }

            com.hashout.aem.aiassistant.service.RagService ragService = new com.hashout.aem.aiassistant.service.RagService(
                    openAIEndpoint, openAIKey, embeddingDeployment, chatDeployment
            );
            String answer = ragService.answerQuestion(question);
            AskQuestionResponse response = new AskQuestionResponse(answer);
            return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(mapper.writeValueAsString(response))
                    .build();
        } catch (Exception e) {
            context.getLogger().severe("Unhandled error: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "application/json")
                    .body("{\"error\":\"" + sanitize(e.getMessage()) + "\"}")
                    .build();
        }
    }

    private boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
    private String firstNonEmpty(String... values) {
        for (String v : values) { if (!isBlank(v)) return v; }
        return null;
    }
    private String sanitize(String msg) {
        if (msg == null) return "";
        return msg.replace("\n", " ").replace("\r", " ");
    }
    private HttpResponseMessage badRequest(HttpRequestMessage<Optional<String>> request, String message) {
        return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body("{\"error\":\"" + message.replace("\"","'") + "\"}")
                .build();
    }
}
