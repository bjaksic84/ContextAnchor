-- Enable the pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================
-- Documents table: stores uploaded document metadata
-- ============================================
CREATE TABLE documents (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    filename        VARCHAR(500) NOT NULL,
    original_name   VARCHAR(500) NOT NULL,
    content_type    VARCHAR(100) NOT NULL,
    file_size       BIGINT NOT NULL,
    page_count      INTEGER,
    status          VARCHAR(50) NOT NULL DEFAULT 'UPLOADED',
    error_message   TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================================
-- Document chunks table: stores text chunks from documents
-- ============================================
CREATE TABLE document_chunks (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    document_id     UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    content         TEXT NOT NULL,
    chunk_index     INTEGER NOT NULL,
    page_number     INTEGER,
    token_count     INTEGER,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_chunks_document_id ON document_chunks(document_id);
CREATE INDEX idx_chunks_chunk_index ON document_chunks(document_id, chunk_index);

-- ============================================
-- Chat conversations table
-- ============================================
CREATE TABLE conversations (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    title           VARCHAR(500),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================================
-- Chat messages table
-- ============================================
CREATE TABLE chat_messages (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    role            VARCHAR(20) NOT NULL,
    content         TEXT NOT NULL,
    sources         JSONB,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_messages_conversation_id ON chat_messages(conversation_id);

-- ============================================
-- Document-Conversation link table (which docs are in scope)
-- ============================================
CREATE TABLE conversation_documents (
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    document_id     UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    PRIMARY KEY (conversation_id, document_id)
);
