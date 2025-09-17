# AEM to Azure AI Search Sync - Java Azure Functions

## Overview
This project provides a Java-based Azure Functions backend to automatically sync content from an AEM (Adobe Experience Manager) export servlet to an Azure AI Search index, using Azure OpenAI for vector embeddings.

It supports:
- HTTP on‑demand sync trigger
- (Optional) timer trigger (disabled/commented locally if storage emulator not present)
- Chunking via Azure OpenAI Chat Completions (with fallback naive split)
- Embedding generation (Azure OpenAI Embeddings deployment)
- Optional Azure AI Search indexing (skipped if search env vars missing)
- Basic or Bearer authentication to AEM

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
### 1. Prerequisites
- Java 17
- Maven 3.8+  
- (Optional) Azurite / Azure Storage connection string if you want the timer trigger

### 2. Configure Environment (`local.settings.json`)
Copy `local.settings.sample.json` to `local.settings.json` and fill in values:
```
cp local.settings.sample.json local.settings.json
```
Key settings (all under `Values`):

| Name | Required | Description |
|------|----------|-------------|
| FUNCTIONS_WORKER_RUNTIME | yes | Should be `java` |
| AzureWebJobsStorage | for timer | `UseDevelopmentStorage=true` for local or real conn string |
| AEM_EXPORT_SERVLET_URL | yes | AEM export servlet endpoint returning JSON array |
| AEM_USERNAME / AEM_PASSWORD | one of auth | Basic auth credentials |
| AEM_AUTH_TOKEN | alternative | Bearer token (overrides Basic if present) |
| AZURE_OPENAI_ENDPOINT | for chunk/embeddings | Your Azure OpenAI endpoint URL |
| AZURE_OPENAI_API_KEY or AZURE_OPENAI_KEY | for chunk/embeddings | API key (either variable accepted) |
| AZURE_OPENAI_CHAT_DEPLOYMENT | for chunk | Chat deployment name used to segment text |
| AZURE_OPENAI_EMBEDDING_DEPLOYMENT | for embeddings | Embedding deployment name |
| AZURE_SEARCH_ENDPOINT | optional | If set + key, enables indexing |
| AZURE_SEARCH_API_KEY | optional | API key for Search service |

If `AZURE_SEARCH_ENDPOINT` or `AZURE_SEARCH_API_KEY` is missing, the upload phase is skipped (fetch + chunk + embed still run).

### 3. Build
```
mvn clean package -DskipTests
```

### 4. Run Functions Host
```
mvn azure-functions:run -DskipTests
```
You should see:
```
SyncAemContentHttp: [POST] http://localhost:7071/api/SyncAemContentHttp
```

### 5. Invoke Sync (New Terminal)
```
curl -i -X POST http://localhost:7071/api/SyncAemContentHttp
```
Expected log sequence:
1. HTTP triggered SyncAemContent
2. AEM response status: 200 (else fix auth)
3. Chunked into N segments via OpenAI (or fallback warning)
4. Embedding chunk X ...
5. Uploaded M text chunks to Azure Search (if configured)

### 6. Local JSON File Test (Offline Parsing)
If you only want to test parsing:
1. Place sample JSON in `src/main/resources/sample-aem-response.json`.
2. Run:
  ```
  mvn exec:java -Dexec.mainClass="com.aem.sync.service.SyncAemContentTestMain"
  ```

### 7. Timer Trigger
The timer trigger is ENABLED in the current code and uses a configurable schedule placeholder in the annotation:

```
@TimerTrigger(name = "syncAemTimer", schedule = "%SYNC_CRON_SCHEDULE%")
```

Provide the cron expression via `SYNC_CRON_SCHEDULE` in `local.settings.json`. Example (run every hour on the hour):

```
"SYNC_CRON_SCHEDULE": "0 0 * * * *"
```

Cron Format (6 fields): `second minute hour day month day-of-week`

Common Examples:
- Every 15 minutes: `0 */15 * * * *`
- Every day at 02:30: `0 30 2 * * *`
- Every 5 minutes: `0 */5 * * * *`

If you do NOT want the timer locally (no storage emulator): either remove `SYNC_CRON_SCHEDULE` or comment the method out again.

### 8. Cleaning & Re-running
If you change code:
```
mvn clean package -DskipTests && mvn azure-functions:run -DskipTests
```

### 9. Deployment
```
mvn azure-functions:deploy
```

### 10. Error Troubleshooting
| Symptom | Likely Cause | Fix |
|---------|--------------|-----|
| 401 from AEM | Bad credentials / token | Verify username/password or set AEM_AUTH_TOKEN |
| OpenAI chunking warning + fallback | Missing chat deployment / key | Set AZURE_OPENAI_CHAT_DEPLOYMENT & key |
| Embeddings failure | Missing embedding deployment | Set AZURE_OPENAI_EMBEDDING_DEPLOYMENT |
| Skips Search upload | Missing search endpoint/key | Provide both or ignore if intentional |
| Host fails needing storage | Timer still enabled | Comment out timer or add AzureWebJobsStorage |

## Retry & Resilience
The service implements exponential (linear-plus-jitter) style backoff for key external calls.

Environment variables (all optional):
| Variable | Applies To | Default | Description |
|----------|------------|---------|-------------|
| SYNC_MAX_AEM_RETRIES | AEM export fetch | 3 | Number of attempts for fetching AEM JSON (non-2xx or IO errors). |
| SYNC_AEM_BACKOFF_MS  | AEM export fetch | 500 | Base backoff (ms) multiplied by attempt number (1..n). |
| SYNC_MAX_OPENAI_RETRIES | Chat chunking | 3 | Attempts for OpenAI chat segmentation. |
| SYNC_OPENAI_BACKOFF_MS | Chat chunking | 750 | Base backoff for chat retry. |
| SYNC_MAX_EMBED_RETRIES | Embedding generation | 2 | Per-chunk embedding retries before skipping that chunk. |
| SYNC_EMBED_BACKOFF_MS | Embedding generation | 600 | Base backoff (ms) between embedding attempts. |

Behavior:
1. On transient failure (non-2xx or exception), waits `base * attemptNumber` ms (adds small random 0–100ms jitter) then retries.
2. After final failure on AEM or chat: aborts that phase (chat fallback to naive split; AEM aborts entire sync).
3. After final embedding failure for a chunk: logs warning and skips that chunk (other chunks still processed).

If none of these variables are set, the defaults above are applied automatically.


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

## Quick Reference Commands
```
# Build
mvn clean package -DskipTests

# Run host
mvn azure-functions:run -DskipTests

# Invoke sync
curl -i -X POST http://localhost:7071/api/SyncAemContentHttp

# Deploy
mvn azure-functions:deploy
```

---
For further details, see inline comments in each class or ask for specific usage examples.
