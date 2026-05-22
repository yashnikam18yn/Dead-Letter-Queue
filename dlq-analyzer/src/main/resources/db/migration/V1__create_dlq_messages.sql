CREATE TABLE dlq_messages (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    message_id VARCHAR(255) NOT NULL,
    broker_type VARCHAR(20) NOT NULL,
    source_destination VARCHAR(500) NOT NULL,
    payload TEXT NOT NULL,
    headers JSON,
    error_class VARCHAR(500),
    error_message TEXT,
    stack_trace TEXT,
    group_key VARCHAR(64),
    failure_count INT NOT NULL DEFAULT 1,
    first_failed_at DATETIME NOT NULL,
    last_failed_at DATETIME NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);