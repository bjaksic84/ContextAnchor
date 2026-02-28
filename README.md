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
| Text Extraction | Apache Tika || Authentication | Spring Security + JWT (JJWT) |
| Multi-tenancy | Row-level tenant isolation || DB Migrations | Flyway |
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

### Authentication

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/auth/register` | Register new user + organization |
| `POST` | `/api/v1/auth/login` | Login (returns JWT tokens) |
| `POST` | `/api/v1/auth/refresh` | Refresh access token |
| `POST` | `/api/v1/auth/logout` | Invalidate refresh token |

### Documents (requires Bearer token)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/documents` | Upload a document (multipart/form-data) |
| `GET`  | `/api/v1/documents` | List all documents |
| `GET`  | `/api/v1/documents/{id}` | Get document details |
| `DELETE` | `/api/v1/documents/{id}` | Delete document + embeddings |

### Chat (requires Bearer token)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/chat` | Ask a question (RAG pipeline) |
| `GET`  | `/api/v1/chat/conversations` | List all conversations |
| `GET`  | `/api/v1/chat/conversations/{id}` | Get conversation with history |
| `DELETE` | `/api/v1/chat/conversations/{id}` | Delete a conversation |

### API Keys (requires Bearer token)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/api-keys` | Create a new API key |
| `GET`  | `/api/v1/api-keys` | List active API keys |
| `DELETE` | `/api/v1/api-keys/{id}` | Revoke an API key |

### Audit Logs (requires Bearer token)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET`  | `/api/v1/audit` | Query audit logs (filterable, paginated) |

### System

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET`  | `/api/v1/health` | Health check (DB, uptime, runtime) |
| `GET`  | `/actuator/prometheus` | Prometheus metrics |
| `GET`  | `/swagger-ui.html` | Interactive API docs |

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
├── audit/
│   ├── AuditAction.java                    # Audit event type constants
│   ├── AuditLog.java                       # Audit record entity
│   ├── AuditLogRepository.java             # Tenant-scoped audit queries
│   └── AuditService.java                   # Async audit event recording
├── config/
│   ├── AsyncConfig.java                    # Thread pool for async processing
│   ├── OpenApiConfig.java                  # Swagger/OpenAPI configuration
│   ├── RequestLoggingFilter.java           # Correlation IDs + request timing
│   └── SecurityConfig.java                 # Spring Security filter chain
├── controller/
│   ├── ApiKeyController.java               # API key CRUD endpoints
│   ├── AuditController.java                # Audit log query endpoint
│   ├── AuthController.java                 # Auth REST endpoints
│   ├── ChatController.java                 # Chat REST endpoints
│   ├── DocumentController.java             # Document REST endpoints
│   └── HealthController.java               # Health check (DB, uptime, runtime)
├── domain/
│   ├── dto/                                 # Request/response DTOs
│   └── entity/                              # JPA entities
│       ├── ApiKey.java                      # API key (SHA-256 hashed)
│       ├── ChatMessage.java
│       ├── Conversation.java
│       ├── Document.java
│       ├── DocumentChunk.java
│       ├── DocumentStatus.java
│       ├── RefreshToken.java
│       ├── Tenant.java
│       ├── User.java
│       └── UserRole.java
├── exception/
│   ├── DocumentNotFoundException.java
│   ├── DocumentProcessingException.java
│   └── GlobalExceptionHandler.java          # Centralized error handling
├── ratelimit/
│   ├── RateLimitConfig.java                # Rate limit configuration
│   ├── RateLimitExceededException.java     # Custom 429 exception
│   ├── RateLimitFilter.java                # Per-tenant rate limiting filter
│   └── RateLimitService.java               # Bucket4j token bucket management
├── repository/
│   ├── ApiKeyRepository.java               # API key lookup by hash
│   ├── ChatMessageRepository.java
│   ├── ConversationRepository.java
│   ├── DocumentChunkRepository.java
│   ├── DocumentRepository.java
│   ├── RefreshTokenRepository.java
│   ├── TenantRepository.java
│   └── UserRepository.java
├── security/
│   ├── JwtAuthenticationFilter.java         # Dual auth: JWT + API Key
│   ├── JwtService.java                      # JWT generation & validation
│   └── SecurityContext.java                 # Current user/tenant utility
└── service/
    ├── ApiKeyService.java                   # API key creation & validation
    ├── AuthService.java                     # Registration, login, tokens
    ├── ChunkingService.java                 # Sentence-aware text chunking
    ├── CustomUserDetailsService.java        # Spring Security user loader
    ├── DocumentExtractionService.java       # PDF/DOCX text extraction (Tika)
    ├── DocumentService.java                 # Document lifecycle orchestrator
    ├── EmbeddingService.java                # Vector embedding generation
    └── RagChatService.java                  # Core RAG pipeline
```

## Roadmap

- [x] **Phase 1** — Core RAG Pipeline ✅
- [x] **Phase 2** — Authentication (Spring Security + JWT), multi-tenancy ✅
- [x] **Phase 3** — Rate limiting, audit logging, API keys, observability, 48 tests ✅
- [ ] **Phase 4** — Ollama support for fully local/private deployment

## License

MIT
