CREATE TABLE conversations (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    document_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(id,owner_id),
    FOREIGN KEY(document_id,owner_id) REFERENCES documents(id,owner_id) ON DELETE CASCADE
);
CREATE INDEX ix_conversations_owner ON conversations(owner_id,created_at DESC,id DESC);
CREATE TABLE chat_turns (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    request_id UUID NOT NULL,
    turn_index INTEGER NOT NULL CHECK(turn_index>=0),
    question VARCHAR(1000) NOT NULL,
    answer TEXT NOT NULL,
    found BOOLEAN NOT NULL,
    sources JSONB NOT NULL CHECK(jsonb_typeof(sources)='array'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(conversation_id,request_id),
    UNIQUE(conversation_id,turn_index),
    FOREIGN KEY(conversation_id,owner_id) REFERENCES conversations(id,owner_id) ON DELETE CASCADE
);
CREATE INDEX ix_turns_owner ON chat_turns(owner_id,conversation_id,turn_index);
CREATE TABLE chat_daily_usage (
    usage_day DATE NOT NULL,
    scope VARCHAR(50) NOT NULL,
    requests BIGINT NOT NULL CHECK(requests>=0),
    input_characters BIGINT NOT NULL CHECK(input_characters>=0),
    PRIMARY KEY(usage_day,scope)
);
