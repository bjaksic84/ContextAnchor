# Enterprise RAG Platform — ContextAnchor

An enterprise-grade **Retrieval-Augmented Generation (RAG)** platform built with **Spring Boot 3**, **Spring AI**, and a **React** frontend. Upload documents and chat with them using AI — runs in the cloud (OpenAI) or fully on-premise (Ollama). Your data stays private.

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
| Backend | Spring Boot 3.4 + Java 21 |
| Frontend | React 18 + Vite 6 + Tailwind CSS 3 |
| AI Integration | Spring AI (OpenAI or Ollama — switchable) |
| Vector Database | PostgreSQL + pgvector |
| Text Extraction | Apache Tika |
| Authentication | Spring Security + JWT (JJWT) |
| Multi-tenancy | Row-level tenant isolation |
| DB Migrations | Flyway |
| API Docs | OpenAPI 3 / Swagger UI |
| Async Processing | Spring @Async with thread pool |
| Containerization | Docker Compose |

## Architecture

```
┌──────────────┐     ┌──────────────────────────────────────────────┐
│   React SPA  │     │           Spring Boot Application            │
│  (Vite :5173)│────▶│                                              │
│              │     │  ┌────────────┐  ┌────────────────────────┐ │
│  or Swagger/ │◀────│  │ REST API   │  │  Document Pipeline     │ │
│   curl/API   │     │  │ Controllers│  │                        │ │
└──────────────┘     │  └─────┬──────┘  │  Upload → Extract →    │ │
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
                     │  │  or  │ │ (search) │                     │
                     │  │Ollama│ │          │                     │
                     │  └──────┘ └──────────┘                     │
                     └──────────────────────────────────────────────┘
```

## Getting Started

### Prerequisites

- **Java 21+**
- **Node.js 18+** (for the frontend)
- **Docker & Docker Compose**
- **OpenAI API key** (for cloud mode) or **Ollama** installed locally (for private mode)

### Quick Start (Local/Private Mode — no API key needed)

```bash
# 1. Clone the repository
git clone https://github.com/bojanjaksic/enterprise-rag-platform.git
cd enterprise-rag-platform

# 2. Start the infrastructure (Postgres + pgvector + Ollama)
docker compose --profile local up -d

# 3. Start the backend (waits for Ollama models to download on first run)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# 4. Start the frontend (in a new terminal)
cd frontend
npm install
npx vite --host
```

Open **http://localhost:5173** — you're ready to go.

### Quick Start (Cloud Mode — OpenAI)

```bash
# 1. Start the database
docker compose up -d

# 2. Set your OpenAI API key
export OPENAI_API_KEY=sk-your-key-here

# 3. Start the backend
./mvnw spring-boot:run

# 4. Start the frontend
cd frontend && npm install && npx vite --host
```

### Using the UI

1. **Register** — open http://localhost:5173, click "Create account", enter your name, email, password, and organization name
2. **Upload Documents** — go to the **Documents** page, drag & drop PDF/DOCX/TXT files into the upload zone. Watch the status change from UPLOADED → PROCESSING → CHUNKING → EMBEDDING → READY
3. **Chat** — go to the **Chat** page, select documents to chat with, type your question. The AI will answer based on your documents with source citations
4. **API Keys** — go to **API Keys** to create keys for programmatic access (REST API). The raw key is shown once — copy and save it
5. **Audit Logs** — go to **Audit Logs** to see all activity in your organization (logins, uploads, chats, etc.)
6. **Settings** — view your profile, organization info, and live system status (AI provider, database, memory)

### Using the REST API directly

You can also use the API without the UI (e.g., from scripts, curl, or Postman):

```bash
# Register
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"fullName":"Alice","email":"alice@example.com","password":"pass1234","organizationName":"My Corp"}'

# Login (save the accessToken)
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"pass1234"}' | jq -r .accessToken)

# Upload a document
DOC_ID=$(curl -s -X POST http://localhost:8080/api/v1/documents \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@my-document.pdf" | jq -r .id)

# Wait for processing, then chat
curl -X POST http://localhost:8080/api/v1/chat \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"question\":\"Summarize the key points\",\"documentIds\":[\"$DOC_ID\"]}"
```

### Swagger UI

Navigate to **http://localhost:8080/swagger-ui.html** for the interactive API documentation.

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
├── frontend/                                # React SPA (Vite + Tailwind CSS)
│   ├── src/
│   │   ├── api/client.js                   # API client with JWT auto-refresh
│   │   ├── context/AuthContext.jsx         # Auth state management
│   │   ├── components/
│   │   │   ├── Layout.jsx                  # Sidebar navigation + system status
│   │   │   ├── ChatMessage.jsx             # Message bubble with source citations
│   │   │   └── ProtectedRoute.jsx          # Auth guard
│   │   └── pages/
│   │       ├── LoginPage.jsx               # Split-screen login
│   │       ├── RegisterPage.jsx            # Registration with org setup
│   │       ├── ChatPage.jsx                # ChatGPT-style RAG interface
│   │       ├── DocumentsPage.jsx           # Drag & drop upload + status tracking
│   │       ├── ApiKeysPage.jsx             # API key management
│   │       ├── AuditLogsPage.jsx           # Activity log with filters
│   │       └── SettingsPage.jsx            # Profile, org, system status
│   ├── package.json
│   └── vite.config.js                      # API proxy to :8080
│
├── src/main/java/com/ragengine/           # Spring Boot backend
│   ├── EnterpriseRagPlatformApplication.java
│   ├── audit/                              # Async audit logging
│   ├── config/                             # Security, AI provider, CORS
│   ├── controller/                         # REST endpoints
│   ├── domain/                             # DTOs + JPA entities
│   ├── exception/                          # Global error handling
│   ├── ratelimit/                          # Per-tenant rate limiting
│   ├── repository/                         # Data access
│   ├── security/                           # JWT + API Key auth
│   └── service/                            # Business logic + RAG pipeline
│
├── docker-compose.yml                      # PostgreSQL + pgvector + Ollama
├── pom.xml                                 # Maven (Spring Boot 3.4)
└── TECHNICAL.md                            # Detailed technical documentation
```

## Roadmap

- [x] **Phase 1** — Core RAG Pipeline (Tika, chunking, embeddings, chat with citations) ✅
- [x] **Phase 2** — Authentication (Spring Security + JWT), multi-tenancy ✅
- [x] **Phase 3** — Rate limiting, audit logging, API keys, observability, 48 tests ✅
- [x] **Phase 4** — Ollama integration, local/private mode, profile-based switching, 54 tests ✅
- [x] **Phase 5** — React UI (Vite + Tailwind), full platform coverage ✅

## License

MIT
