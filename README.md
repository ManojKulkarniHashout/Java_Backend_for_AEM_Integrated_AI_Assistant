# Java Backend for AEM Integrated AI Assistant

A Java Azure Functions application that automatically syncs content from Adobe Experience Manager (AEM) to Azure AI Search with vector embeddings for intelligent search capabilities.

## Features

- **Scheduled Sync**: Automatically syncs AEM content on a configurable schedule (default: hourly)
- **On-Demand Sync**: HTTP endpoint for manual content synchronization
- **Vector Embeddings**: Generates embeddings using Azure OpenAI for semantic search
- **Azure AI Search Integration**: Stores content with metadata and vector search capabilities
- **Secure Authentication**: Uses Azure Identity for secure service authentication

## Architecture

The solution consists of:

1. **SyncAemContent**: Timer-triggered function for scheduled synchronization
2. **SyncAemContentOnDemand**: HTTP-triggered function for manual sync
3. **AemDataService**: Service for fetching content from AEM Data Export Servlet
4. **EmbeddingService**: Service for generating vector embeddings using Azure OpenAI
5. **SearchService**: Service for managing Azure AI Search index and documents

## Prerequisites

- Java 17 or higher
- Maven 3.6 or higher
- Azure subscription with:
  - Azure Functions App
  - Azure OpenAI Service
  - Azure AI Search Service
- AEM instance with Data Export Servlet configured

## Configuration

Update `local.settings.json` with your Azure service credentials:

```json
{
  "IsEncrypted": false,
  "Values": {
    "AzureWebJobsStorage": "your_storage_connection_string",
    "FUNCTIONS_WORKER_RUNTIME": "java",
    
    "AZURE_CLIENT_ID": "your_azure_client_id",
    "AZURE_CLIENT_SECRET": "your_azure_client_secret",
    "AZURE_TENANT_ID": "your_azure_tenant_id",
    
    "AZURE_OPENAI_ENDPOINT": "https://your-openai-resource.openai.azure.com/",
    "AZURE_OPENAI_API_KEY": "your_openai_api_key",
    "AZURE_OPENAI_DEPLOYMENT_NAME": "your_embedding_model_deployment",
    
    "AZURE_SEARCH_ENDPOINT": "https://your-search-service.search.windows.net",
    "AZURE_SEARCH_API_KEY": "your_search_api_key",
    "AZURE_SEARCH_INDEX_NAME": "aem-content-index",
    
    "AEM_DATA_EXPORT_URL": "https://your-aem-instance.com/bin/export/data",
    "AEM_USERNAME": "your_aem_username",
    "AEM_PASSWORD": "your_aem_password",
    
    "SYNC_SCHEDULE": "0 0 */1 * * *"
  }
}
```

## Building and Deployment

### Local Development

1. Clone the repository
2. Configure `local.settings.json` with your service credentials
3. Build the project:
   ```bash
   mvn clean package
   ```
4. Run locally:
   ```bash
   mvn azure-functions:run
   ```

### Azure Deployment

1. Deploy to Azure Functions:
   ```bash
   mvn azure-functions:deploy
   ```

2. Configure application settings in Azure Portal with the same environment variables from `local.settings.json`

## API Endpoints

### Scheduled Sync
- **Function**: `SyncAemContent`
- **Trigger**: Timer (configured via `SYNC_SCHEDULE`)
- **Default Schedule**: Every hour (`0 0 */1 * * *`)

### On-Demand Sync
- **Function**: `SyncAemContentOnDemand`
- **Method**: `POST`
- **URL**: `/api/SyncAemContentOnDemand`
- **Authentication**: Function key required

### Example Usage
```bash
# Trigger on-demand sync
curl -X POST "https://your-function-app.azurewebsites.net/api/SyncAemContentOnDemand?code=your_function_key"
```

## Data Model

The application processes AEM content items with the following structure:

```java
{
  "jcr:path": "/content/example/page",
  "jcr:title": "Page Title",
  "jcr:description": "Page description",
  "cq:lastModified": "2023-01-01T12:00:00Z",
  "cq:tags": ["tag1", "tag2"],
  "content": "Page content text",
  "contentType": "page",
  "author": "author@example.com"
}
```

## Search Index Schema

The Azure AI Search index includes:

- **id**: Unique document identifier
- **path**: AEM content path
- **title**: Content title
- **description**: Content description
- **content**: Full content text
- **textChunk**: Human-readable text for embedding
- **contentVector**: Vector embedding (1536 dimensions)
- **lastModified**: Last modification timestamp
- **tags**: Content tags
- **author**: Content author
- **contentType**: Type of content

## Monitoring and Logging

The application provides comprehensive logging for:
- Sync process status
- Content fetch operations
- Embedding generation
- Search index operations
- Error handling and troubleshooting

View logs in Azure Functions monitoring or locally during development.

## Error Handling

The application includes robust error handling for:
- AEM connectivity issues
- Azure OpenAI API failures
- Azure AI Search indexing errors
- Configuration validation
- Network timeouts

## Security

- Uses Azure Key Credentials for service authentication
- Supports Basic Authentication for AEM access
- Function key required for on-demand sync endpoint
- No credentials stored in code

## Development

### Project Structure
```
src/
├── main/java/com/hashout/aem/aiassistant/
│   ├── Function.java                 # Main Azure Functions
│   ├── model/
│   │   └── AemContentItem.java       # AEM content POJO
│   └── service/
│       ├── AemDataService.java       # AEM data fetching
│       ├── EmbeddingService.java     # OpenAI embeddings
│       └── SearchService.java        # Azure Search operations
└── test/
    └── java/com/hashout/aem/aiassistant/
        └── FunctionTest.java         # Unit tests
```

### Adding New Features

1. Create new service classes in `src/main/java/com/hashout/aem/aiassistant/service/`
2. Add corresponding tests in `src/test/`
3. Update configuration in `local.settings.json`
4. Build and test locally before deployment

## License

This project is licensed under the MIT License.
