-- refresh_tokens was created without a foreign key or supporting indexes, yet it is queried and
-- deleted by user_id (logout / deactivate) and by expires_at (hourly cleanup job).

ALTER TABLE refresh_tokens
    ADD CONSTRAINT fk_refresh_tokens_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

CREATE INDEX idx_refresh_tokens_user_id    ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);
