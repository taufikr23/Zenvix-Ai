CREATE TABLE documents (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users(id),
    original_filename VARCHAR(255) NOT NULL,
    media_type VARCHAR(120) NOT NULL,
    file_type VARCHAR(8) NOT NULL CHECK (file_type IN ('PDF','DOCX','TXT')),
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
    sha256 CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'UPLOADED'
        CHECK (status IN ('UPLOADED','QUEUED','PROCESSING','READY','FAILED','DELETING')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (id, owner_id)
);
CREATE INDEX ix_documents_owner_created ON documents(owner_id, created_at DESC, id DESC);
