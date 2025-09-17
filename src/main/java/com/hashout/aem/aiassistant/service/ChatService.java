package com.hashout.aem.aiassistant.service;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.*;
import com.azure.core.credential.AzureKeyCredential;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Service for generating chat completions using Azure OpenAI
 */
public class ChatService {
    
    private static final Logger LOGGER = Logger.getLogger(ChatService.class.getName());
    
    private final OpenAIClient openAIClient;
    private final String deploymentName;
    
    public ChatService(String endpoint, String apiKey, String deploymentName) {
        this.deploymentName = deploymentName;
        
        this.openAIClient = new OpenAIClientBuilder()
                .endpoint(endpoint)
                .credential(new AzureKeyCredential(apiKey))
                .buildClient();
    }
    
    /**
     * Generates a chat completion response given a user question and relevant context
     */
    public String generateChatResponse(String userQuestion, List<String> contextChunks) {
        try {
            LOGGER.info("Generating chat response for question: " + userQuestion.substring(0, Math.min(100, userQuestion.length())));
            
            // Create system message with context
            String systemPrompt = createSystemPrompt(contextChunks);
            
            List<ChatRequestMessage> chatMessages = new ArrayList<>();
            chatMessages.add(new ChatRequestSystemMessage(systemPrompt));
            chatMessages.add(new ChatRequestUserMessage(userQuestion));
            
            ChatCompletionsOptions chatCompletionsOptions = new ChatCompletionsOptions(chatMessages)
                    .setMaxTokens(1000)
                    .setTemperature(0.3)
                    .setTopP(0.9);
            
            ChatCompletions chatCompletions = openAIClient.getChatCompletions(deploymentName, chatCompletionsOptions);
            
            if (chatCompletions.getChoices().isEmpty()) {
                throw new RuntimeException("No chat completion choices received from OpenAI");
            }
            
            String response = chatCompletions.getChoices().get(0).getMessage().getContent();
            LOGGER.info("Successfully generated chat response with length: " + response.length());
            
            return response;
            
        } catch (Exception e) {
            LOGGER.severe("Failed to generate chat response: " + e.getMessage());
            throw new RuntimeException("Failed to generate chat response", e);
        }
    }
    
    /**
     * Creates a system prompt with relevant context information
     */
    private String createSystemPrompt(List<String> contextChunks) {
        StringBuilder promptBuilder = new StringBuilder();
        
        promptBuilder.append("You are an AI assistant for an Adobe Experience Manager (AEM) system. ");
        promptBuilder.append("Answer questions based on the provided AEM content context. ");
        promptBuilder.append("If you cannot find the answer in the context, say so clearly. ");
        promptBuilder.append("Be concise and accurate in your responses.\n\n");
        
        if (contextChunks != null && !contextChunks.isEmpty()) {
            promptBuilder.append("Relevant AEM content context:\n");
            for (int i = 0; i < contextChunks.size(); i++) {
                promptBuilder.append("Context ").append(i + 1).append(":\n");
                promptBuilder.append(contextChunks.get(i)).append("\n\n");
            }
        } else {
            promptBuilder.append("No relevant context found in the AEM system.\n\n");
        }
        
        promptBuilder.append("Please answer the user's question based on the above context.");
        
        return promptBuilder.toString();
    }
}