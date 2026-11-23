-- Preserve existing messages and allow long pasted text without VARCHAR truncation.
ALTER TABLE chat_turns ALTER COLUMN question TYPE TEXT;
