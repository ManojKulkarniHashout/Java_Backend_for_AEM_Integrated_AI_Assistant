# AEM to Azure AI Search Sync - Java Azure Functions

## Overview
This project provides a Java-based Azure Functions backend to automatically sync content from an AEM (Adobe Experience Manager) export servlet to an Azure AI Search index, using Azure OpenAI for vector embeddings.

## Main Components

### 1. `SyncAemContentFunction`
- **Location:** `src/main/java/com/aem/sync/SyncAemContentFunction.java`
- **Purpose:** Defines Azure Functions triggers:
  - `SyncAemContentTimer`: Timer-triggered function (runs every hour)
  - `SyncAemContentHttp`: HTTP-triggered function for on-demand sync
- **Usage:** Calls the sync logic in `SyncAemContentService`.

### 2. `SyncAemContentService`
- **Location:** `src/main/java/com/aem/sync/service/SyncAemContentService.java`
- **Purpose:** Implements the core sync logic:
  - Fetches JSON data from the AEM export servlet (via OkHttp)
  - Parses JSON into `AemContent` POJOs
  - Generates human-readable text chunks for embedding
  - Calls Azure OpenAI to generate vector embeddings
  - Uploads chunks, vectors, and metadata to Azure AI Search
- **Testing:** Includes `syncFromFile` for local testing with a sample JSON file.

### 3. `AemContent`
- **Location:** `src/main/java/com/aem/sync/model/AemContent.java`
- **Purpose:** POJO representing a single AEM content item. Maps all fields from the AEM JSON response. Provides `toChunk()` to generate a concise text chunk for embedding.

### 4. `SearchDocument`
- **Location:** `src/main/java/com/aem/sync/model/SearchDocument.java`
- **Purpose:** POJO representing a document to be indexed in Azure AI Search. Contains the text chunk, vector embedding, and raw metadata.

### 5. `SyncAemContentTestMain`
- **Location:** `src/main/java/com/aem/sync/service/SyncAemContentTestMain.java`
- **Purpose:** Standalone main class for local testing. Reads a sample JSON file and prints parsed chunks to the console.

## How to Test Locally
1. Place your sample AEM JSON response in `src/main/resources/sample-aem-response.json`.
2. Run the test main class:
   ```
   mvn exec:java -Dexec.mainClass="com.aem.sync.service.SyncAemContentTestMain"
   ```
3. Check the console output for parsed chunks and summary.

## Azure Integration
- Configure Azure service endpoints and keys in `local.settings.json`.
- Deploy using:
  ```
  mvn azure-functions:deploy
  ```
- Monitor sync and index population in the Azure Portal.

## Dependencies
- Azure Functions Java Library
- Azure Search Documents SDK
- Azure OpenAI SDK
- Azure Identity SDK
- OkHttp (HTTP client)
- Jackson (JSON parsing)

## Notes
- The sync logic is designed to be secure and scalable.
- For production, ensure all secrets are managed securely (e.g., Azure Key Vault).
- The code is modular for easy extension and testing.

---
For further details, see inline comments in each class or ask for specific usage examples.
