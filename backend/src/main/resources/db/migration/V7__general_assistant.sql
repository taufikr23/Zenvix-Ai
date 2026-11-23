-- Additive migration: retain existing document chats, accounts, files and messages.
ALTER TABLE conversations ALTER COLUMN document_id DROP NOT NULL;
ALTER TABLE conversations ADD COLUMN title VARCHAR(100) NOT NULL DEFAULT 'Percakapan baru';
UPDATE conversations c SET title = left(d.original_filename, 100)
FROM documents d WHERE c.document_id = d.id AND c.owner_id = d.owner_id;
ALTER TABLE conversations ADD CONSTRAINT fk_conversations_owner
    FOREIGN KEY(owner_id) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE chat_turns ALTER COLUMN question TYPE VARCHAR(12000);
