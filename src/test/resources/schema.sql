-- JWT Keys Table
CREATE TABLE IF NOT EXISTS jwt_keys (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    key_id VARCHAR(255) NOT NULL UNIQUE,
    public_key TEXT NOT NULL,
    private_key TEXT NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    rotated_at TIMESTAMP
);

-- JWT Refresh Tokens Table
CREATE TABLE IF NOT EXISTS jwt_refresh_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    token_value VARCHAR(500) NOT NULL UNIQUE,
    access_token_id VARCHAR(255),
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_refresh_member_id ON jwt_refresh_tokens (member_id);
CREATE INDEX IF NOT EXISTS idx_refresh_access_token_id ON jwt_refresh_tokens (access_token_id);
CREATE INDEX IF NOT EXISTS idx_refresh_expires_at ON jwt_refresh_tokens (expires_at);
CREATE INDEX IF NOT EXISTS idx_refresh_token_value ON jwt_refresh_tokens (token_value);

-- JWT Blacklist Table
CREATE TABLE IF NOT EXISTS jwt_blacklist (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    jti VARCHAR(255) NOT NULL UNIQUE,
    member_id BIGINT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_blacklist_jti ON jwt_blacklist (jti);
CREATE INDEX IF NOT EXISTS idx_blacklist_expires_at ON jwt_blacklist (expires_at);
CREATE INDEX IF NOT EXISTS idx_blacklist_member_id ON jwt_blacklist (member_id);
