CREATE TABLE transactions (
    id           UUID      PRIMARY KEY,
    user_id      UUID      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    recipient_id UUID      REFERENCES users(id) ON DELETE SET NULL,
    type         VARCHAR   NOT NULL CHECK (type IN ('deposit', 'withdraw', 'transfer')),
    amount       BIGINT    NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_transactions_user_id      ON transactions(user_id);
CREATE INDEX idx_transactions_recipient_id ON transactions(recipient_id);
CREATE INDEX idx_transactions_created_at   ON transactions(created_at DESC);
