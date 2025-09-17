# AEM Azure Chatbot

This Azure Function exposes a `/api/AskQuestion` endpoint that implements Retrieval-Augmented Generation (RAG) using Azure OpenAI and Azure AI Search over AEM data.

## Environment Variables
Set the following environment variables for deployment:
- `AZURE_OPENAI_ENDPOINT`: Azure OpenAI endpoint URL
- `AZURE_OPENAI_KEY`: Azure OpenAI API key
- `AZURE_OPENAI_EMBEDDING_MODEL`: Embedding model name (e.g., "text-embedding-ada-002")
- `AZURE_OPENAI_CHAT_MODEL`: Chat model name (e.g., "gpt-35-turbo")
- `AZURE_SEARCH_ENDPOINT`: Azure AI Search endpoint URL
- `AZURE_SEARCH_KEY`: Azure AI Search API key
- `AZURE_SEARCH_INDEX_NAME`: Name of the Azure Search index containing AEM data

## Usage
Send a POST request to `/api/AskQuestion` with JSON body:
```json
{
  "question": "What is the latest update in AEM?"
}
```
Response:
```json
{
  "answer": "..."
}
```

## Deployment
Deploy as a Java Azure Function App. Ensure all environment variables are set and the AEM data is indexed in Azure AI Search.
