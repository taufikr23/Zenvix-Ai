-- Support rich visual search and image attachments per conversation turn.
ALTER TABLE chat_turns ADD COLUMN images JSONB NOT NULL DEFAULT '[]'::jsonb;
