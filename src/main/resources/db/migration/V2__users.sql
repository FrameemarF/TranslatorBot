-- Профили пользователей и настройки по умолчанию.
CREATE TABLE IF NOT EXISTS users (
    platform VARCHAR(32) NOT NULL,
    account_id BIGINT NOT NULL,
    user_name TEXT,
    registered_at TIMESTAMP WITH TIME ZONE NOT NULL,
    target_lang VARCHAR(8),
    response_mode VARCHAR(16) NOT NULL,
    time_zone VARCHAR(64),
    PRIMARY KEY (platform, account_id)
);
