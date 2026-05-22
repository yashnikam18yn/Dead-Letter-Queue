CREATE INDEX idx_dlq_messages_group_key ON dlq_messages(group_key);
CREATE INDEX idx_dlq_messages_status ON dlq_messages(status);
CREATE INDEX idx_dlq_messages_broker_type ON dlq_messages(broker_type);
CREATE INDEX idx_dlq_messages_created_at ON dlq_messages(created_at);
CREATE INDEX idx_replay_audit_log_batch_id ON replay_audit_log(batch_id);
CREATE INDEX idx_replay_audit_log_message_id ON replay_audit_log(message_id);