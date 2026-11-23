ALTER TABLE documents DROP CONSTRAINT documents_status_check;
ALTER TABLE documents ADD CONSTRAINT documents_status_check
    CHECK (status IN ('UPLOADED','QUEUED','PROCESSING','EXTRACTED','READY','FAILED','DELETING'));
ALTER TABLE documents ALTER COLUMN status SET DEFAULT 'QUEUED';
ALTER TABLE documents ADD COLUMN chunk_count INTEGER NOT NULL DEFAULT 0 CHECK (chunk_count >= 0),
    ADD COLUMN extracted_characters INTEGER NOT NULL DEFAULT 0 CHECK (extracted_characters >= 0),
    ADD COLUMN source_pages INTEGER,
    ADD COLUMN processed_at TIMESTAMPTZ,
    ADD COLUMN processing_error VARCHAR(64);
UPDATE documents SET status='QUEUED' WHERE status='UPLOADED';
CREATE INDEX ix_documents_queue ON documents(created_at,id) WHERE status='QUEUED';

CREATE TABLE document_chunks (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    chunk_index INTEGER NOT NULL CHECK (chunk_index >= 0),
    source_section INTEGER NOT NULL CHECK (source_section >= 1),
    page_number INTEGER CHECK (page_number >= 1),
    content TEXT NOT NULL CHECK (length(content) > 0),
    character_count INTEGER NOT NULL CHECK (character_count > 0),
    FOREIGN KEY (document_id,owner_id) REFERENCES documents(id,owner_id) ON DELETE CASCADE,
    UNIQUE(document_id,chunk_index)
);
CREATE INDEX ix_chunks_owner_document ON document_chunks(owner_id,document_id,chunk_index);
