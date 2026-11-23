-- Fondasi akun; endpoint registrasi dan login belum dibuat pada tahap 1.
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(254) NOT NULL CHECK (email = btrim(email) AND length(email) > 3),
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(16) NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN')),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Email dengan huruf besar/kecil berbeda tetap dianggap akun yang sama.
CREATE UNIQUE INDEX ux_users_email_lower ON users (lower(email));
