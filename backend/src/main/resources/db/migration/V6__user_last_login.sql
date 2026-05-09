-- DS Deviation §4.2.1: track last login timestamp for access review.

ALTER TABLE users ADD COLUMN last_login_at TIMESTAMPTZ;
CREATE INDEX idx_users_last_login_at ON users(last_login_at);

-- Envers _AUD table parity
ALTER TABLE users_aud ADD COLUMN last_login_at TIMESTAMPTZ;
