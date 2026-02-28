# ContextAnchor — Technical Documentation

> **Last updated:** Phase 3 — Enterprise Features  
> **Status:** Phase 1 Complete ✅ | Phase 2 Complete ✅ | Phase 3 Complete ✅ | Phase 4 Planned

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Project Structure](#project-structure)
3. [Core Pipeline: How RAG Works](#core-pipeline-how-rag-works)
4. [Authentication & Multi-tenancy](#authentication--multi-tenancy)
5. [Enterprise Features (Phase 3)](#enterprise-features-phase-3)
6. [Layer-by-Layer Breakdown](#layer-by-layer-breakdown)
7. [Database Schema](#database-schema)
8. [API Reference](#api-reference)
9. [Configuration](#configuration)
10. [Testing](#testing)
11. [Phase Checkpoints](#phase-checkpoints)

---

## Architecture Overview

ContextAnchor is a **Retrieval-Augmented Generation (RAG)** platform. The core idea: instead of asking an LLM to answer from its training data (which may be outdated or hallucinated), we **retrieve** the most relevant pieces from your own uploaded documents and **inject** them into the prompt as context. The LLM then answers grounded in your actual data.

```
                            ┌─────────────────────────┐
                            │       REST API           │
                            │  /api/v1/documents       │
                            │  /api/v1/chat            │
                            │  /api/v1/auth            │
                            │  /api/v1/api-keys        │
                            │  /api/v1/audit           │
                            └────────┬────────────────┘
                                     │
                      ┌──────────────┼──────────────┐
                      │   Request Logging Filter     │
                      │   (correlation IDs, timing)  │
                      └──────────────┬──────────────┘
                                     │
                      ┌──────────────┼──────────────┐
                      │   Rate Limit Filter          │
                      │   (Bucket4j per-tenant)      │
                      └──────────────┬──────────────┘
                                     │
                      ┌──────────────┼──────────────┐
                      │   JWT / API Key Auth Filter  │
                      │   (dual authentication)      │
                      └──────────────┬──────────────┘
                                     │
           ┌─────────────────┬───────┴───────┬──────────────┐
           ▼                 ▼               ▼              ▼
    ┌──────────────┐ ┌──────────────┐ ┌──────────┐ ┌──────────────┐
    │  Document     │ │  RAG Chat    │ │  Auth    │ │  API Key /   │
    │  Controller   │ │  Controller  │ │  Ctrl    │ │  Audit Ctrl  │
    └──────┬───────┘ └──────┬───────┘ └────┬─────┘ └──────┬───────┘
           │                │              │              │
           ▼                ▼              ▼              ▼
    ┌──────────────┐ ┌──────────────┐ ┌──────────┐ ┌──────────────┐
    │  Document     │ │  RagChat     │ │  Auth    │ │  ApiKey /    │
    │  Service      │ │  Service     │ │  Service │ │  Audit Svc   │
    │(rate-limited) │ │(rate-limited)│ │(audited) │ │  (async)     │
    └──┬───┬───┬───┘ └──┬───────┬───┘ └──────────┘ └──────────────┘
       │   │   │        │       │
       ▼   ▼   ▼        ▼       ▼
 ┌──────┐┌──────┐┌──────┐┌──────┐┌──────────┐
 │Extract││Chunk ││Embed ││Vector││  OpenAI  │
 │Service││Svc   ││Svc   ││Store ││  LLM     │
 │(Tika) ││      ││      ││(pg)  ││          │
 └──────┘└──────┘└──────┘└──────┘└──────────┘
                                   │
                            ┌──────┴──────┐
                            │ PostgreSQL  │
                            │ + pgvector  │
                            └─────────────┘
```

### Data Flow

**Upload flow:**
```
HTTP POST (file) → DocumentController → DocumentService
  → [async] DocumentExtractionService (Apache Tika extracts text)
  → ChunkingService (splits into overlapping chunks)
  → EmbeddingService (generates vectors via Spring AI → stores in pgvector)
  → Document status: READY
```

**Chat flow:**
```
HTTP POST (question + docIds) → ChatController → RagChatService
  → VectorStore.similaritySearch (finds top-K relevant chunks)
  → Builds augmented prompt with retrieved context
  → ChatClient sends to OpenAI with conversation history
  → Returns answer with source citations
```

---

## Project Structure

```
src/main/java/com/ragengine/
├── EnterpriseRagPlatformApplication.java   # Spring Boot entry point
│
├── audit/                                   # Audit logging subsystem
│   ├── AuditAction.java                    # Constants for audit event types
│   ├── AuditLog.java                       # JPA entity for audit records
│   ├── AuditLogRepository.java             # Tenant-scoped audit queries
│   └── AuditService.java                   # Async audit event recording
│
├── config/                                  # Configuration beans
│   ├── AsyncConfig.java                    # Thread pool for async doc processing
│   ├── OpenApiConfig.java                  # Swagger/OpenAPI metadata
│   ├── RequestLoggingFilter.java           # Correlation IDs + request timing
│   └── SecurityConfig.java                 # Spring Security filter chain, BCrypt, CORS
│
├── controller/                              # REST endpoints (thin layer)
│   ├── ApiKeyController.java               # POST/GET/DELETE API keys
│   ├── AuditController.java                # GET audit logs (paginated)
│   ├── AuthController.java                 # POST register/login/refresh/logout
│   ├── ChatController.java                 # POST /chat, GET/DELETE conversations
│   ├── DocumentController.java             # POST/GET/DELETE documents
│   └── HealthController.java              # GET /health (DB, runtime, uptime)
│
├── domain/                                  # Data models
│   ├── dto/                                # Request/response objects (Java records)
│   │   ├── AuthResponse.java              # accessToken + refreshToken + userInfo
│   │   ├── ChatRequest.java               # question + documentIds + conversationId
│   │   ├── ChatResponse.java              # answer + sources[] + conversationId
│   │   ├── ConversationResponse.java      # conversation with messages
│   │   ├── DocumentResponse.java          # document metadata + status
│   │   ├── LoginRequest.java              # email + password
│   │   ├── RefreshTokenRequest.java       # refreshToken
│   │   └── RegisterRequest.java           # name + email + password + organizationName
│   └── entity/                            # JPA entities (database tables)
│       ├── ApiKey.java                    # API key entity (SHA-256 hashed)
│       ├── ChatMessage.java               # Single message in a conversation
│       ├── Conversation.java              # Chat session (tenant-scoped)
│       ├── Document.java                  # Uploaded document (tenant-scoped)
│       ├── DocumentChunk.java             # Individual text chunk from document
│       ├── DocumentStatus.java            # Enum: UPLOADED→...→READY|FAILED
│       ├── RefreshToken.java              # Opaque refresh token entity
│       ├── Tenant.java                    # Organization / tenant entity
│       ├── User.java                      # User entity (implements UserDetails)
│       └── UserRole.java                  # Enum: ADMIN, USER
│
├── exception/                               # Error handling
│   ├── DocumentNotFoundException.java
│   ├── DocumentProcessingException.java
│   └── GlobalExceptionHandler.java         # All exceptions incl. auth (401/403)
│
├── ratelimit/                               # Rate limiting subsystem
│   ├── RateLimitConfig.java                # ConfigurationProperties for limits
│   ├── RateLimitExceededException.java     # Custom 429 exception
│   ├── RateLimitFilter.java                # HTTP filter (per-tenant buckets)
│   └── RateLimitService.java               # Bucket4j token bucket management
│
├── repository/                              # Spring Data JPA interfaces
│   ├── ApiKeyRepository.java               # API key lookup by hash
│   ├── ChatMessageRepository.java
│   ├── ConversationRepository.java         # Tenant-scoped queries
│   ├── DocumentChunkRepository.java
│   ├── DocumentRepository.java             # Tenant-scoped queries
│   ├── RefreshTokenRepository.java
│   ├── TenantRepository.java
│   └── UserRepository.java
│
├── security/                                # Authentication & authorization
│   ├── JwtAuthenticationFilter.java        # Dual auth: JWT + API Key (X-API-Key)
│   ├── JwtService.java                     # Generate/validate tokens, extract claims
│   └── SecurityContext.java                # Utility: get current user/tenant
│
└── service/                                 # Business logic
    ├── ApiKeyService.java                  # API key creation, validation, revocation
    ├── AuthService.java                    # Register, login, refresh, logout (audited)
    ├── ChunkingService.java                # Text splitting engine
    ├── CustomUserDetailsService.java       # Loads User for Spring Security
    ├── DocumentExtractionService.java      # PDF/DOCX text extraction (Tika)
    ├── DocumentService.java                # Upload orchestrator (rate-limited, audited)
    ├── EmbeddingService.java               # Vector generation & storage
    └── RagChatService.java                 # Core RAG pipeline (rate-limited, audited)
```

---

## Core Pipeline: How RAG Works

### 1. Document Extraction (`DocumentExtractionService`)

**What it does:** Takes raw file bytes (PDF, DOCX, TXT) and extracts plain text.

**How:** Uses Apache Tika's `AutoDetectParser` which automatically identifies the file format and applies the correct parser. The `BodyContentHandler(-1)` allows unlimited text extraction.

**Key decisions:**
- We use individual Tika parser modules (pdf, microsoft, text) instead of `tika-parsers-standard-package` to keep the dependency footprint manageable
- Page count is extracted from PDF metadata (`xmpTPg:NPages`) when available

### 2. Text Chunking (`ChunkingService`)

**What it does:** Splits extracted text into overlapping chunks suitable for embedding.

**Why chunking matters:** LLMs have context windows. Embeddings work best on focused, coherent text. A 50-page PDF as one embedding would be too diluted — a 200-word chunk about a specific topic creates a much more precise vector.

**Strategy: Sentence-aware chunking with overlap**

```
Original text: [S1. S2. S3. S4. S5. S6. S7. S8. S9. S10.]

Chunk 1: [S1. S2. S3. S4. S5.]           ← ~800 chars
Chunk 2:          [S4. S5. S6. S7. S8.]   ← overlaps with chunk 1
Chunk 3:                   [S7. S8. S9. S10.] ← overlaps with chunk 2
                   ▲
                   └── Overlap ensures no context is lost at boundaries
```

**Configuration (application.yml):**
- `chunk-size: 800` — target characters per chunk
- `chunk-overlap: 200` — characters of overlap between consecutive chunks
- `min-chunk-size: 100` — discard chunks smaller than this

**Fallback:** If no sentence boundaries are detected (e.g., code files), falls back to fixed-size splitting at word boundaries.

### 3. Embedding & Storage (`EmbeddingService`)

**What it does:** Converts text chunks into vector embeddings and stores them in PostgreSQL via pgvector.

**How it works:**
1. Each chunk is wrapped in a Spring AI `Document` with metadata (documentId, documentName, chunkIndex, pageNumber)
2. `VectorStore.add()` automatically:
   - Sends chunk text to OpenAI's `text-embedding-3-small` model
   - Receives a 1536-dimensional vector back
   - Stores the vector + text + metadata in the `vector_store` table (managed by Spring AI)
3. pgvector uses an HNSW index for fast approximate nearest-neighbor search

**Why `text-embedding-3-small`:** Good balance of quality vs cost. 1536 dimensions. Fast. Much cheaper than `text-embedding-3-large` (3072 dims) with minimal quality loss for RAG use cases.

### 4. RAG Chat (`RagChatService`)

**What it does:** Orchestrates the full retrieve → augment → generate pipeline.

**Step-by-step:**

```
1. VALIDATE   → Check all requested documents are in READY status
2. RETRIEVE   → vectorStore.similaritySearch(question, topK=5, filter=documentIds)
                 Converts question to embedding, finds most similar chunks
3. CONTEXT    → Formats retrieved chunks into labeled context:
                 "[Source 1 - report.pdf, Chunk 3] ..."
4. HISTORY    → Loads previous messages from conversation (up to 10)
5. AUGMENT    → Builds prompt: system prompt + history + context + question
6. GENERATE   → ChatClient sends to OpenAI GPT-4o-mini
7. CITE       → Extracts source metadata from retrieved chunks
8. PERSIST    → Saves user question + AI answer to conversation
```

**Filter expression:** When searching the vector store, we filter by both `tenantId` and `documentId` so results are always scoped to the user's organization — complete cross-tenant isolation at the vector level.

**Conversation memory:** Multi-turn chat is supported. Previous messages are loaded and included in the prompt. Limited to `max-history-size: 10` messages to stay within the context window.

---

## Authentication & Multi-tenancy

### JWT Authentication Flow

ContextAnchor uses stateless JWT authentication. No server-side sessions.

```
┌────────┐                    ┌──────────────┐                ┌──────────┐
│ Client │                    │  Auth API    │                │ Database │
└───┬────┘                    └──────┬───────┘                └────┬─────┘
    │  POST /auth/register           │                             │
    │  {name, email, pass, org}      │                             │
    │───────────────────────────────►│  Create Tenant + User       │
    │                                │────────────────────────────►│
    │  ◄─ {accessToken, refreshToken}│                             │
    │                                │                             │
    │  POST /auth/login              │                             │
    │  {email, password}             │                             │
    │───────────────────────────────►│  Verify BCrypt hash         │
    │                                │────────────────────────────►│
    │  ◄─ {accessToken, refreshToken}│                             │
    │                                │                             │
    │  GET /api/v1/documents         │                             │
    │  Authorization: Bearer <jwt>   │                             │
    │───────────────────────────────►│  JwtAuthFilter validates    │
    │                                │  Loads User from DB         │
    │                                │  Sets SecurityContext       │
    │  ◄─ tenant-scoped results      │────────────────────────────►│
    │                                │                             │
    │  POST /auth/refresh            │                             │
    │  {refreshToken}                │                             │
    │───────────────────────────────►│  Validate refresh token     │
    │  ◄─ {new accessToken}          │  Issue new access token     │
```

### JWT Token Structure

**Access Token (15 min):**
```json
{
  "sub": "user@example.com",
  "userId": "uuid",
  "tenantId": "uuid",
  "role": "USER",
  "iat": 1709136000,
  "exp": 1709136900
}
```

**Refresh Token (7 days):** Opaque token stored in the database. Used to issue new access tokens without re-authenticating.

### Multi-tenancy Model

Every organization gets a `Tenant` entity. Every user belongs to exactly one tenant. All data is scoped by `tenant_id`.

```
┌─────────────────────────────────────────────────────┐
│                    Tenant A                          │
│  ┌──────────┐  ┌──────────┐  ┌──────────────────┐  │
│  │ User 1   │  │ User 2   │  │ Documents        │  │
│  │ (ADMIN)  │  │ (USER)   │  │ (tenant_id = A)  │  │
│  └──────────┘  └──────────┘  │ Conversations    │  │
│                               │ (tenant_id = A)  │  │
│                               │ Embeddings       │  │
│                               │ (tenantId = A)   │  │
│                               └──────────────────┘  │
└─────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────┐
│                    Tenant B                          │
│  ┌──────────┐                ┌──────────────────┐   │
│  │ User 3   │                │ Documents        │   │
│  │ (ADMIN)  │                │ (tenant_id = B)  │   │
│  └──────────┘                │ Completely        │   │
│                               │ isolated from A  │   │
│                               └──────────────────┘   │
└─────────────────────────────────────────────────────┘
```

**Isolation is enforced at 3 levels:**
1. **Repository queries** — all queries include `WHERE tenant_id = ?`
2. **Vector store** — similarity search filters by `tenantId` metadata
3. **SecurityContext** — tenant ID is extracted from the JWT, never from user input

### Security Components

| Class | Responsibility |
|-------|---------------|
| `JwtService` | Generate & validate JWT tokens, extract claims |
| `JwtAuthenticationFilter` | Intercepts requests, validates JWT or API Key, sets SecurityContext |
| `SecurityContext` | Utility to get current user/tenant from Spring SecurityContextHolder |
| `SecurityConfig` | Filter chain, BCrypt encoder, public endpoints, CORS, stateless sessions |
| `AuthService` | Registration, login, token refresh, logout logic |
| `CustomUserDetailsService` | Loads User entity for Spring Security |
| `ApiKeyService` | Create/validate/revoke API keys (SHA-256 hashed) |
| `RateLimitFilter` | Per-tenant rate limiting (Bucket4j token buckets) |
| `RequestLoggingFilter` | Correlation IDs (X-Request-Id), timing, status logging |

### Password Security

Passwords are hashed with **BCrypt** (work factor 10). Raw passwords are never stored.

---

## Enterprise Features (Phase 3)

### Rate Limiting

Per-tenant rate limiting using **Bucket4j** token-bucket algorithm. Three independent buckets per tenant:

| Bucket | Default Limit | Applies To |
|--------|--------------|------------|
| API requests | 60 / minute | All authenticated endpoints |
| Chat requests | 20 / minute | `/api/v1/chat` |
| Upload requests | 30 / hour | `POST /api/v1/documents` |

**Architecture:**
```
Request → RequestLoggingFilter (@Order 1)
        → RateLimitFilter     (@Order 2) → 429 if exceeded
        → JwtAuthFilter       (Security) → 401/403 if invalid
        → Controller
```

- Buckets are created lazily per tenant on first request
- Greedy refill strategy (tokens replenish continuously)
- Responds with HTTP 429 + `Retry-After` header when limit exceeded
- Can be disabled entirely via `rag.rate-limit.enabled=false`

### Audit Logging

All security-relevant actions are recorded asynchronously to an `audit_logs` table:

| Action | Trigger |
|--------|---------|
| `USER_REGISTER` | New user registration |
| `USER_LOGIN` / `USER_LOGIN_FAILED` | Login attempts |
| `TOKEN_REFRESH` | Access token refresh |
| `DOCUMENT_UPLOAD` / `DOCUMENT_DELETE` | Document lifecycle |
| `CHAT_QUERY` | RAG queries |
| `CONVERSATION_DELETE` | Conversation deletion |
| `API_KEY_CREATED` / `API_KEY_REVOKED` | API key lifecycle |
| `RATE_LIMIT_EXCEEDED` | Rate limit violations |

- **Async recording** — audit writes happen on a separate thread (`@Async`) so they don't slow down the main request
- FK constraints on `tenant_id` / `user_id` are intentionally removed (`V4` migration) to avoid race conditions with async writes
- Queryable via `GET /api/v1/audit` with filters for action type, user ID, and pagination

### API Key Authentication

Alternative to JWT for service-to-service or programmatic access:

```
Client generates key → POST /api/v1/api-keys → receives raw key (ctx_<32 hex chars>)
                        (key is SHA-256 hashed before storage — raw key shown ONCE)

Subsequent requests → X-API-Key: ctx_abc123...
                   → JwtAuthFilter hashes the key, looks up in DB
                   → Sets SecurityContext with the key owner's identity
```

- Keys are stored as SHA-256 hashes (raw key never stored)
- Prefix-based identification (`ctx_` + first 8 chars visible for management)
- Optional expiration dates
- Can be revoked without affecting other keys
- Dual auth: filter checks `X-API-Key` header first, then falls back to `Bearer` JWT

### Request Logging & Correlation IDs

Every request receives a unique correlation ID (`X-Request-Id` header):

- If the client sends an `X-Request-Id`, it's preserved
- Otherwise, a UUID is generated
- Logged at request start and completion with timing, status, and method
- Enables request tracing across distributed systems

### Health Check Enhancements

`GET /api/v1/health` now reports:

```json
{
  "status": "UP",
  "timestamp": "2025-01-15T10:30:00",
  "uptime": "2h 15m 30s",
  "database": "CONNECTED",
  "java": "25",
  "springBoot": "3.4.3"
}
```

---

## Layer-by-Layer Breakdown

### Controllers (REST Layer)
- **Thin layer** — no business logic, just HTTP concerns
- Validates request bodies using Jakarta Bean Validation (`@Valid`)
- Returns proper HTTP status codes (201 Created, 204 No Content, etc.)
- Annotated with OpenAPI `@Operation` for auto-generated Swagger docs

### Services (Business Logic)
- **DocumentService** — the orchestrator. Handles upload validation, file storage, and kicks off async processing
- **`@Async`** — document processing runs on a separate thread pool (configured in `AsyncConfig`) so the upload endpoint returns immediately while processing continues in the background
- **Status tracking** — document status transitions: UPLOADED → PROCESSING → CHUNKING → EMBEDDING → READY (or FAILED with error message)

### Repositories (Data Access)
- Pure Spring Data JPA interfaces — no implementation needed
- Query derivation from method names (e.g., `findByDocumentIdOrderByChunkIndex`)

### Exception Handling (`GlobalExceptionHandler`)
- `@RestControllerAdvice` catches all exceptions globally
- Consistent JSON error format: `{ timestamp, status, error, message }`
- Handles: not found, validation errors, processing errors, file size limits, unexpected errors

### Configuration
- **AsyncConfig** — 2 core threads, max 5, queue capacity 25 for document processing
- **OpenApiConfig** — Swagger UI metadata and descriptions
- **application.yml** — all tunable parameters (chunk size, model, top-K, etc.)

---

## Database Schema

### Tables (managed by Flyway — `V1__init_schema.sql` through `V4__drop_audit_fk_constraints.sql`)

```sql
tenants                      -- Organizations / tenants
├── id (UUID, PK)
├── name                     -- Organization name (unique)
├── slug                     -- URL-friendly identifier (unique)
├── created_at, updated_at

users                        -- User accounts
├── id (UUID, PK)
├── tenant_id (FK → tenants)
├── name                     -- Display name
├── email                    -- Login email (unique)
├── password                 -- BCrypt hash
├── role                     -- ADMIN or USER
├── enabled                  -- Account active flag
├── created_at, updated_at

refresh_tokens               -- JWT refresh tokens
├── id (UUID, PK)
├── user_id (FK → users)
├── token                    -- Opaque token string (unique)
├── expires_at               -- Expiration timestamp
├── created_at

documents                    -- Uploaded file metadata
├── id (UUID, PK)
├── tenant_id (FK → tenants) -- Tenant isolation
├── uploaded_by (FK → users) -- Who uploaded
├── filename                 -- UUID-prefixed stored filename
├── original_name            -- Original upload filename
├── content_type             -- MIME type
├── file_size                -- Bytes
├── page_count               -- From PDF metadata (nullable)
├── status                   -- UPLOADED|PROCESSING|CHUNKING|EMBEDDING|READY|FAILED
├── error_message            -- Populated on FAILED
├── created_at, updated_at

document_chunks              -- Text chunks extracted from documents
├── id (UUID, PK)
├── document_id (FK → documents) -- CASCADE DELETE
├── content (TEXT)           -- The actual chunk text
├── chunk_index              -- Order within document
├── page_number              -- Source page (nullable)
├── token_count              -- Estimated tokens (~chars/4)
├── created_at

conversations                -- Chat sessions
├── id (UUID, PK)
├── tenant_id (FK → tenants) -- Tenant isolation
├── created_by (FK → users)  -- Who started the conversation
├── title                    -- Auto-set from first question
├── created_at, updated_at

chat_messages                -- Individual messages in conversations
├── id (UUID, PK)
├── conversation_id (FK → conversations) -- CASCADE DELETE
├── role                     -- "user" or "assistant"
├── content (TEXT)           -- Message text
├── sources (JSONB)          -- Citation data (assistant messages only)
├── created_at

conversation_documents       -- Many-to-many: which docs are in scope
├── conversation_id (FK)
├── document_id (FK)
```

audit_logs                   -- Security audit trail
├── id (UUID, PK)
├── tenant_id (UUID)         -- Tenant context (no FK — async writes)
├── user_id (UUID)           -- Acting user (no FK — async writes)
├── action                   -- Audit action constant
├── resource_type            -- Entity type affected (nullable)
├── resource_id              -- Entity ID affected (nullable)
├── details                  -- Additional context (nullable)
├── ip_address               -- Client IP (nullable)
├── created_at

api_keys                     -- API key credentials
├── id (UUID, PK)
├── tenant_id (FK → tenants)
├── user_id (FK → users)
├── name                     -- Human-readable key name
├── key_hash                 -- SHA-256 hash (unique)
├── key_prefix               -- First 8 chars for identification
├── active                   -- Can be revoked
├── expires_at               -- Optional expiration
├── last_used_at             -- Usage tracking
├── created_at
```

**Additionally:** Spring AI automatically manages a `vector_store` table for pgvector embeddings.

---

## API Reference

### Auth API

| Endpoint | Method | Description | Request | Response |
|----------|--------|-------------|---------|----------|
| `/api/v1/auth/register` | POST | Register user + tenant | RegisterRequest JSON | 200 + AuthResponse |
| `/api/v1/auth/login` | POST | Login | LoginRequest JSON | 200 + AuthResponse |
| `/api/v1/auth/refresh` | POST | Refresh access token | RefreshTokenRequest JSON | 200 + AuthResponse |
| `/api/v1/auth/logout` | POST | Invalidate refresh token | RefreshTokenRequest JSON | 200 |

### Documents API

| Endpoint | Method | Description | Request | Response |
|----------|--------|-------------|---------|----------|
| `/api/v1/documents` | POST | Upload document | `multipart/form-data` (file) | 201 + DocumentResponse |
| `/api/v1/documents` | GET | List all docs | — | DocumentResponse[] |
| `/api/v1/documents/{id}` | GET | Get doc by ID | — | DocumentResponse |
| `/api/v1/documents/{id}` | DELETE | Delete doc + embeddings | — | 204 |

### Chat API

| Endpoint | Method | Description | Request | Response |
|----------|--------|-------------|---------|----------|
| `/api/v1/chat` | POST | Ask question (RAG) | ChatRequest JSON | ChatResponse |
| `/api/v1/chat/conversations` | GET | List conversations | — | ConversationResponse[] |
| `/api/v1/chat/conversations/{id}` | GET | Get conversation | — | ConversationResponse |
| `/api/v1/chat/conversations/{id}` | DELETE | Delete conversation | — | 204 |

### API Keys (requires Bearer token)

| Endpoint | Method | Description | Request | Response |
|----------|--------|-------------|---------|----------|
| `/api/v1/api-keys` | POST | Create API key | `{ "name": "...", "expiresInDays": 90 }` | 200 + key (shown once) |
| `/api/v1/api-keys` | GET | List active keys | — | ApiKeyResponse[] |
| `/api/v1/api-keys/{id}` | DELETE | Revoke a key | — | 204 |

### Audit Logs (requires Bearer token)

| Endpoint | Method | Description | Request | Response |
|----------|--------|-------------|---------|----------|
| `/api/v1/audit` | GET | Query audit logs | `?action=&userId=&page=&size=` | Page\<AuditLogResponse\> |

### System

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/health` | GET | Health check (DB status, uptime, runtime) |
| `/swagger-ui.html` | GET | Interactive API docs |
| `/api-docs` | GET | OpenAPI JSON spec |
| `/actuator/prometheus` | GET | Prometheus metrics |

---

## Configuration

### Key application.yml parameters

| Property | Default | Description |
|----------|---------|-------------|
| `security.jwt.secret` | env var | JWT signing secret (min 32 chars) |
| `security.jwt.access-token-expiration` | 900000 (15m) | Access token TTL in ms |
| `security.jwt.refresh-token-expiration` | 604800000 (7d) | Refresh token TTL in ms |
| `spring.ai.openai.api-key` | env var | OpenAI API key |
| `spring.ai.openai.chat.options.model` | gpt-4o-mini | Chat model |
| `spring.ai.openai.embedding.options.model` | text-embedding-3-small | Embedding model |
| `spring.ai.vectorstore.pgvector.dimensions` | 1536 | Must match embedding model |
| `rag.chunking.chunk-size` | 800 | Target chunk size (chars) |
| `rag.chunking.chunk-overlap` | 200 | Overlap between chunks |
| `rag.chunking.min-chunk-size` | 100 | Minimum chunk size |
| `rag.chat.top-k-results` | 5 | Number of chunks retrieved per query |
| `rag.chat.max-history-size` | 10 | Max messages in conversation context |
| `rag.upload.storage-path` | ./uploads | File storage directory |
| `rag.rate-limit.enabled` | true | Enable/disable rate limiting |
| `rag.rate-limit.requests-per-minute` | 60 | General API rate limit per tenant |
| `rag.rate-limit.chat-requests-per-minute` | 20 | Chat endpoint rate limit per tenant |
| `rag.rate-limit.uploads-per-hour` | 30 | Upload endpoint rate limit per tenant |

---

## Testing

### Test Infrastructure

| Component | Purpose |
|-----------|--------|
| **Testcontainers** | Spins up a real PostgreSQL 16 + pgvector instance in Docker for integration tests |
| **BaseIntegrationTest** | Shared base class — manages the container lifecycle, sets dynamic datasource properties |
| **TestAiConfig** | `@TestConfiguration` providing stub `VectorStore` and `ChatClient.Builder` (no real AI calls in tests) |
| **application-test.yml** | Test profile config — disables rate limiting, uses test JWT secret |

### Test Suite Summary

| Test Class | Type | Tests | Covers |
|------------|------|-------|--------|
| `ChunkingServiceTest` | Unit | 8 | Sentence-aware text splitting, overlap, edge cases |
| `RateLimitServiceTest` | Unit | 10 | Bucket4j token buckets, tenant isolation, disabled mode |
| `ApiKeyServiceTest` | Unit | 3 | SHA-256 hashing consistency and format |
| `AuthControllerIntegrationTest` | Integration | 9 | Registration, login, refresh, logout, error cases |
| `ApiKeyIntegrationTest` | Integration | 7 | API key CRUD, authentication via X-API-Key header |
| `AuditControllerIntegrationTest` | Integration | 5 | Audit log queries, filtering, pagination |
| `HealthControllerIntegrationTest` | Integration | 6 | Health endpoint fields, DB connectivity, public access |
| **Total** | | **48** | |

### Running Tests

```bash
# All tests (requires Docker for Testcontainers)
./mvnw test

# Unit tests only
./mvnw test -Dtest="ChunkingServiceTest,RateLimitServiceTest,ApiKeyServiceTest"

# Integration tests only
./mvnw test -Dtest="*IntegrationTest"
```

> **Note:** Java 25 + Mockito/ByteBuddy incompatibility — tests use manual stub beans (`TestAiConfig`) instead of `@MockitoBean`.

---

## Phase Checkpoints

### ✅ Phase 1 — Core RAG Pipeline (COMPLETE)
- [x] Spring Boot 3.4 + Java 21 project scaffold
- [x] Document upload with async processing pipeline
- [x] Apache Tika text extraction (PDF, DOCX, TXT)
- [x] Sentence-aware text chunking with overlap
- [x] Vector embeddings via Spring AI + pgvector
- [x] RAG chat with similarity search + LLM generation
- [x] Multi-turn conversations with history
- [x] Source citations in responses
- [x] Global exception handling
- [x] OpenAPI/Swagger documentation
- [x] Docker Compose (PostgreSQL + pgvector + pgAdmin)
- [x] Flyway database migrations
- [x] Unit tests (ChunkingService — 8 tests)
- [x] Maven wrapper

### ✅ Phase 2 — Authentication & Multi-tenancy (COMPLETE)
- [x] Spring Security configuration with JWT filter chain
- [x] JWT token generation and validation (JJWT 0.12.6)
- [x] User registration and login endpoints (`/auth/**`)
- [x] Tenant (organization) entity and auto-creation on registration
- [x] Role-based access control (ADMIN, USER)
- [x] Refresh token rotation (7-day refresh, 15-min access)
- [x] Tenant-scoped document isolation (repository + vector store)
- [x] Tenant-scoped conversation isolation
- [x] Password hashing (BCrypt)
- [x] JWT authentication filter
- [x] SecurityContext utility for tenant/user extraction
- [x] Updated all services with tenant-aware queries
- [x] Auth exception handling (401/403 responses)
- [x] Database migration (`V2__auth_multitenancy.sql`)

### ✅ Phase 3 — Enterprise Features (COMPLETE)
- [x] Rate limiting per tenant (Bucket4j token-bucket algorithm)
- [x] Audit logging — async recording of all security-relevant actions
- [x] Micrometer Prometheus metrics endpoint (`/actuator/prometheus`)
- [x] Request/response logging with correlation IDs (X-Request-Id)
- [x] API key authentication (alternative to JWT, SHA-256 hashed)
- [x] Health check enhancements (DB status, uptime, runtime info)
- [x] Integration tests with Testcontainers (27 integration + 21 unit = 48 total)
- [x] Database migrations (`V3__audit_and_api_keys.sql`, `V4__drop_audit_fk_constraints.sql`)

### 📋 Phase 4 — Local/Private Mode (PLANNED)
- [ ] Ollama integration for local LLM inference
- [ ] Local embedding model support
- [ ] Profile-based switching (cloud vs local)
- [ ] Zero external API dependency mode
