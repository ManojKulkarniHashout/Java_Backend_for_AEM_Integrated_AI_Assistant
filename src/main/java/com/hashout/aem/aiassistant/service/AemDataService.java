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
    
    public AemDataService(String aemDataExportUrl, String aemUsername, String aemPassword) {
        this.aemDataExportUrl = aemDataExportUrl;
        this.aemUsername = aemUsername;
        this.aemPassword = aemPassword;
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
        
        // Build request with Basic Auth
        Request request = new Request.Builder()
                .url(aemDataExportUrl)
                .header("Authorization", buildBasicAuthHeader())
                .header("Accept", "application/json")
                .get()
                .build();
        
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
        String credentials = aemUsername + ":" + aemPassword;
        String encodedCredentials = Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encodedCredentials;
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