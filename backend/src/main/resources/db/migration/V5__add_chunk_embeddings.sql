ALTER TABLE documents DROP CONSTRAINT documents_status_check;
ALTER TABLE documents ADD CONSTRAINT documents_status_check CHECK
    (status IN ('UPLOADED','QUEUED','PROCESSING','EXTRACTED','READY','FAILED','DELETING','INDEX_FAILED'));
ALTER TABLE documents ADD COLUMN indexed_chunk_count INTEGER NOT NULL DEFAULT 0 CHECK (indexed_chunk_count>=0),
    ADD COLUMN indexed_at TIMESTAMPTZ, ADD COLUMN indexing_error VARCHAR(64);
ALTER TABLE document_chunks ADD COLUMN embedding VECTOR(768),
    ADD COLUMN embedding_model VARCHAR(100),
    ADD CONSTRAINT ck_chunk_embedding_model CHECK (
        (embedding IS NULL AND embedding_model IS NULL) OR
        (embedding IS NOT NULL AND embedding_model IS NOT NULL AND embedding_model='gemini-embedding-001'));
CREATE INDEX ix_documents_embedding_queue ON documents(created_at,id) WHERE status='EXTRACTED';
CREATE TABLE embedding_daily_usage (
    usage_day DATE NOT NULL,
    scope VARCHAR(50) NOT NULL,
    reserved_characters BIGINT NOT NULL CHECK (reserved_characters>=0),
    PRIMARY KEY(usage_day,scope)
);
