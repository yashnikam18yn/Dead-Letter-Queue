package com.dlqanalyzer.dlq_analyzer.repository;


import com.dlqanalyzer.dlq_analyzer.model.ReplayAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReplayAuditLogRepository extends JpaRepository<ReplayAuditLog, String> {
    List<ReplayAuditLog> findByBatchId(String batchId);
    List<ReplayAuditLog> findByMessageId(String messageId);
}