package com.dlqanalyzer.dlq_analyzer.repository;


import com.dlqanalyzer.dlq_analyzer.model.DlqMessage;
import com.dlqanalyzer.dlq_analyzer.model.MessageStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DlqMessageRepository extends JpaRepository<DlqMessage, String> {
    List<DlqMessage> findByGroupKey(String groupKey);
    List<DlqMessage> findByStatus(MessageStatus status);
    long countByGroupKey(String groupKey);
    boolean existsByMessageId(String messageId);

    // Needed for failure-count aggregation in DlqIngestionService.
    Optional<DlqMessage> findByMessageId(String messageId);
}