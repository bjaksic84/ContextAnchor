-- ============================================
-- Phase 2: Authentication & Multi-tenancy
-- ============================================

-- Tenants (organizations)
CREATE TABLE tenants (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name            VARCHAR(200) NOT NULL UNIQUE,
    slug            VARCHAR(100) NOT NULL UNIQUE,
    max_documents   INTEGER NOT NULL DEFAULT 50,
    max_file_size   BIGINT NOT NULL DEFAULT 52428800,  -- 50MB
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Users
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(200) NOT NULL,
    role            VARCHAR(20) NOT NULL DEFAULT 'USER',
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_tenant_id ON users(tenant_id);
CREATE INDEX idx_users_email ON users(email);

-- Add tenant_id to documents
ALTER TABLE documents ADD COLUMN tenant_id UUID;
ALTER TABLE documents ADD COLUMN uploaded_by UUID;

-- Add tenant_id to conversations
ALTER TABLE conversations ADD COLUMN tenant_id UUID;
ALTER TABLE conversations ADD COLUMN created_by UUID;

-- Set foreign key constraints (after data migration if needed)
ALTER TABLE documents ADD CONSTRAINT fk_documents_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE;
ALTER TABLE documents ADD CONSTRAINT fk_documents_uploaded_by
    FOREIGN KEY (uploaded_by) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE conversations ADD CONSTRAINT fk_conversations_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE;
ALTER TABLE conversations ADD CONSTRAINT fk_conversations_created_by
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL;

CREATE INDEX idx_documents_tenant_id ON documents(tenant_id);
CREATE INDEX idx_conversations_tenant_id ON conversations(tenant_id);

-- Refresh token table (for token rotation)
CREATE TABLE refresh_tokens (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token           VARCHAR(500) NOT NULL UNIQUE,
    expires_at      TIMESTAMP NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
