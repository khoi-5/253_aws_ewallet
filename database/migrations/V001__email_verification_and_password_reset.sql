USE ewallet_db;

ALTER TABLE users ADD COLUMN email VARCHAR(254) NULL AFTER phone;
ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT TRUE AFTER email;

UPDATE users
SET email = CONCAT('user-', id, '@local.invalid')
WHERE role = 'user' AND email IS NULL;

ALTER TABLE users ADD CONSTRAINT uq_users_email UNIQUE (email);
ALTER TABLE users ADD CONSTRAINT chk_regular_user_email CHECK (role = 'admin' OR email IS NOT NULL);
ALTER TABLE users ALTER COLUMN email_verified SET DEFAULT FALSE;

CREATE TABLE account_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    token_type ENUM('EMAIL_VERIFICATION', 'PASSWORD_RESET') NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_account_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_account_tokens_user_type (user_id, token_type, used_at)
);
