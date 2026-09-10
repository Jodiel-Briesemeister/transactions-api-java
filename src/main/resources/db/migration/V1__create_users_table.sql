CREATE TABLE users (
    id          UUID        PRIMARY KEY,
    name        VARCHAR     NOT NULL,
    email       VARCHAR     NOT NULL UNIQUE,
    phone       VARCHAR,
    password_hash VARCHAR   NOT NULL,
    is_active   BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_name  ON users(name);
CREATE INDEX idx_users_email ON users(email);
