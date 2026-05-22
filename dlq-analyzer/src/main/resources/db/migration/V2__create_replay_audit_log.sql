CREATE TABLE replay_audit_log (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    batch_id VARCHAR(36) NOT NULL,
    message_id VARCHAR(36) NOT NULL,
    action VARCHAR(20) NOT NULL,
    target_destination VARCHAR(500),
    performed_by VARCHAR(255) NOT NULL,
    result VARCHAR(20) NOT NULL,
    error_detail TEXT,
    performed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (message_id) REFERENCES dlq_messages(id)
);