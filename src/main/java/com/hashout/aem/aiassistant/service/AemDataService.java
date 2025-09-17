package com.hashout.aem.aiassistant.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hashout.aem.aiassistant.model.AemContentItem;
import okhttp3.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Service for fetching data from AEM Data Export Servlet
 */
public class AemDataService {
    
    private static final Logger LOGGER = Logger.getLogger(AemDataService.class.getName());
    
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String aemDataExportUrl;
    private final String aemUsername;
    private final String aemPassword;
    private final String aemAuthToken; // Optional bearer token
    
    public AemDataService(String aemDataExportUrl, String aemUsername, String aemPassword) {
        this.aemDataExportUrl = aemDataExportUrl;
        this.aemUsername = aemUsername;
        this.aemPassword = aemPassword;
        this.aemAuthToken = System.getenv("AEM_AUTH_TOKEN");
        this.objectMapper = new ObjectMapper();
        
        // Configure HTTP client with timeouts
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }
    
    /**
     * Fetches AEM content items from the Data Export Servlet
     */
    public List<AemContentItem> fetchAemContent() throws IOException {
        LOGGER.info("Fetching AEM content from: " + aemDataExportUrl);
        
        // Build request with appropriate auth (Bearer if token provided, else Basic if username/password provided)
        Request.Builder builder = new Request.Builder()
                .url(aemDataExportUrl)
                .header("Accept", "application/json");

        String authHeader = buildAuthHeader();
        if (authHeader != null) {
            builder.header("Authorization", authHeader);
        } else {
            LOGGER.warning("No AEM authentication configured (AEM_AUTH_TOKEN or AEM_USERNAME/AEM_PASSWORD). Proceeding without auth header.");
        }

        Request request = builder.get().build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to fetch AEM content: " + response.code() + " " + response.message());
            }
            
            String responseBody = response.body().string();
            LOGGER.info("Received response from AEM, parsing JSON...");
            
            // Parse JSON response to list of AemContentItem objects
            TypeReference<List<AemContentItem>> typeRef = new TypeReference<List<AemContentItem>>() {};
            List<AemContentItem> contentItems = objectMapper.readValue(responseBody, typeRef);
            
            LOGGER.info("Successfully parsed " + contentItems.size() + " content items");
            return contentItems;
        }
    }
    
    /**
     * Builds Basic Authentication header
     */
    private String buildBasicAuthHeader() {
        if (aemUsername == null || aemPassword == null) {
            return null;
        }
        String credentials = aemUsername + ":" + aemPassword;
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encodedCredentials;
    }

    /**
     * Builds the correct auth header based on available configuration.
     * Priority: Bearer token (AEM_AUTH_TOKEN) > Basic (AEM_USERNAME/AEM_PASSWORD) > none
     */
    private String buildAuthHeader() {
        if (aemAuthToken != null && !aemAuthToken.isBlank()) {
            return "Bearer " + aemAuthToken.trim();
        }
        return buildBasicAuthHeader();
    }
    
    /**
     * Close the HTTP client resources
     */
    public void close() {
        if (httpClient != null) {
            httpClient.dispatcher().executorService().shutdown();
            httpClient.connectionPool().evictAll();
        }
    }
}