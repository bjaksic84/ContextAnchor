# Enterprise RAG Platform

An enterprise-grade **Retrieval-Augmented Generation (RAG)** platform built with **Spring Boot 3** and **Spring AI**. Upload documents and chat with them using AI — your data stays private.

## What It Does

```
📄 Upload PDF/DOCX/TXT → 🔪 Smart Chunking → 🧮 Vector Embeddings → 💬 Chat with AI
```

1. **Upload** a document (PDF, DOCX, TXT)
2. **Apache Tika** extracts the text content
3. **Chunking engine** splits text into overlapping, sentence-aware chunks
4. **Spring AI** generates vector embeddings and stores them in **pgvector**
5. **Ask questions** — the system retrieves relevant chunks via similarity search and feeds them to an LLM
6. **Get cited answers** — every response includes source references

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Framework | Spring Boot 3.4 + Java 21 |
| AI Integration | Spring AI (OpenAI / Ollama) |
| Vector Database | PostgreSQL + pgvector |
| Text Extraction | Apache Tika |
| DB Migrations | Flyway |
| API Docs | OpenAPI 3 / Swagger UI |
| Async Processing | Spring @Async with thread pool |
| Containerization | Docker Compose |

## Architecture

```
┌──────────────┐     ┌──────────────────────────────────────────────┐
│   Client     │     │           Spring Boot Application            │
│  (Swagger/   │────▶│                                              │
│   Postman)   │     │  ┌────────────┐  ┌────────────────────────┐ │
│              │◀────│  │ REST API   │  │  Document Pipeline     │ │
└──────────────┘     │  │ Controllers│  │                        │ │
                     │  └─────┬──────┘  │  Upload → Extract →    │ │
                     │        │         │  Chunk  → Embed        │ │
                     │        ▼         └───────────┬────────────┘ │
                     │  ┌─────────────┐             │              │
                     │  │ RAG Chat    │◀────────────┘              │
                     │  │ Service     │                            │
                     │  └──┬──────┬───┘                            │
                     │     │      │                                │
                     │     ▼      ▼                                │
                     │  ┌──────┐ ┌──────────┐                     │
                     │  │OpenAI│ │ pgvector │                     │
                     │  │ LLM  │ │ (search) │                     │
                     │  └──────┘ └──────────┘                     │
                     └──────────────────────────────────────────────┘
```

## Getting Started

### Prerequisites

- **Java 21+**
- **Docker & Docker Compose**
- **OpenAI API key** (or a locally running Ollama instance)

### 1. Clone and set up

```bash
git clone https://github.com/bojanjaksic/enterprise-rag-platform.git
cd enterprise-rag-platform
```

### 2. Start the database

```bash
docker compose up -d
```

This starts PostgreSQL with pgvector and pgAdmin (accessible at `http://localhost:5050`).

### 3. Set your OpenAI API key

```bash
export OPENAI_API_KEY=sk-your-key-here
```

### 4. Run the application

```bash
./mvnw spring-boot:run
```

### 5. Open Swagger UI

Navigate to **http://localhost:8080/swagger-ui.html** to explore the API.

## API Endpoints

### Documents

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/documents` | Upload a document (multipart/form-data) |
| `GET`  | `/api/v1/documents` | List all documents |
| `GET`  | `/api/v1/documents/{id}` | Get document details |
| `DELETE` | `/api/v1/documents/{id}` | Delete document + embeddings |

### Chat

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/chat` | Ask a question (RAG pipeline) |
| `GET`  | `/api/v1/chat/conversations` | List all conversations |
| `GET`  | `/api/v1/chat/conversations/{id}` | Get conversation with history |
| `DELETE` | `/api/v1/chat/conversations/{id}` | Delete a conversation |

### Example Chat Request

```json
{
  "question": "What are the key findings in section 3?",
  "documentIds": ["uuid-of-uploaded-document"],
  "conversationId": null
}
```

### Example Chat Response

```json
{
  "conversationId": "generated-uuid",
  "answer": "According to the document, section 3 discusses...",
  "sources": [
    {
      "documentId": "uuid",
      "documentName": "report.pdf",
      "chunkContent": "...relevant excerpt...",
      "chunkIndex": 12,
      "pageNumber": 5,
      "similarityScore": 0.89
    }
  ],
  "timestamp": "2026-02-28T10:30:00"
}
```

## Project Structure

```
src/main/java/com/ragengine/
├── EnterpriseRagPlatformApplication.java    # Main entry point
├── config/
│   ├── AsyncConfig.java                     # Thread pool for async processing
│   └── OpenApiConfig.java                   # Swagger/OpenAPI configuration
├── controller/
│   ├── ChatController.java                  # Chat REST endpoints
│   ├── DocumentController.java              # Document REST endpoints
│   └── HealthController.java                # Health check endpoint
├── domain/
│   ├── dto/                                 # Request/response DTOs
│   │   ├── ChatRequest.java
│   │   ├── ChatResponse.java
│   │   ├── ConversationResponse.java
│   │   └── DocumentResponse.java
│   └── entity/                              # JPA entities
│       ├── ChatMessage.java
│       ├── Conversation.java
│       ├── Document.java
│       ├── DocumentChunk.java
│       └── DocumentStatus.java
├── exception/
│   ├── DocumentNotFoundException.java
│   ├── DocumentProcessingException.java
│   └── GlobalExceptionHandler.java          # Centralized error handling
├── repository/                              # Spring Data JPA repositories
│   ├── ChatMessageRepository.java
│   ├── ConversationRepository.java
│   ├── DocumentChunkRepository.java
│   └── DocumentRepository.java
└── service/
    ├── ChunkingService.java                 # Sentence-aware text chunking
    ├── DocumentExtractionService.java       # PDF/DOCX text extraction (Tika)
    ├── DocumentService.java                 # Document lifecycle orchestrator
    ├── EmbeddingService.java                # Vector embedding generation
    └── RagChatService.java                  # Core RAG pipeline
```

## Roadmap

- [ ] **Phase 1** — Core RAG Pipeline (current) ✅
- [ ] **Phase 2** — Authentication (Spring Security + JWT), multi-tenancy
- [ ] **Phase 3** — Rate limiting, audit logging, observability
- [ ] **Phase 4** — Ollama support for fully local/private deployment

## License

MIT
