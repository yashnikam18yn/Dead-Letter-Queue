-- Enforce idempotency at the database level: a given broker message must exist at most once.
-- Pairs with the stable, deterministic message_id now produced by RabbitMQAdapter and the
-- find-or-aggregate logic in DlqIngestionService.
--
-- NOTE: if a pre-existing database already contains duplicate message_id values, dedupe them
-- before applying this migration, otherwise it will fail.
ALTER TABLE dlq_messages
    ADD CONSTRAINT uq_dlq_messages_message_id UNIQUE (message_id);