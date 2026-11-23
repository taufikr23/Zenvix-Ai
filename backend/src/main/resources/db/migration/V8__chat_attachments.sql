ALTER TABLE chat_turns ADD CONSTRAINT uq_turn_owner UNIQUE(id, owner_id);
CREATE TABLE chat_attachments (
    id UUID PRIMARY KEY,
    turn_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    position INTEGER NOT NULL CHECK(position BETWEEN 0 AND 2),
    filename VARCHAR(255) NOT NULL,
    media_type VARCHAR(100) NOT NULL,
    content BYTEA NOT NULL CHECK(octet_length(content) BETWEEN 1 AND 5242880),
    extracted_text TEXT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    UNIQUE(turn_id, position),
    FOREIGN KEY(turn_id, owner_id) REFERENCES chat_turns(id, owner_id) ON DELETE CASCADE
);
CREATE INDEX ix_chat_attachments_owner ON chat_attachments(owner_id, turn_id);
