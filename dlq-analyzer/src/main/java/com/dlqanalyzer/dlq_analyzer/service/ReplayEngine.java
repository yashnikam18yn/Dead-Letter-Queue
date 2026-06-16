package com.dlqanalyzer.dlq_analyzer.service;


import com.dlqanalyzer.dlq_analyzer.adapter.BrokerAdapter;
import com.dlqanalyzer.dlq_analyzer.model.DlqMessage;
import com.dlqanalyzer.dlq_analyzer.model.MessageStatus;
import com.dlqanalyzer.dlq_analyzer.model.ReplayAuditLog;
import com.dlqanalyzer.dlq_analyzer.repository.DlqMessageRepository;
import com.dlqanalyzer.dlq_analyzer.repository.ReplayAuditLogRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReplayEngine {
    private final List<BrokerAdapter> adapters;
    private final DlqMessageRepository dlqMessageRepository;
    private final ReplayAuditLogRepository replayAuditLogRepository;
    private final WebSocketService webSocketService;

    // Public entry point: a single-message replay gets its own fresh batchId.
    @Transactional
    public String replayMessage(String messageId, String targetDestination, boolean dryRun, String performedBy) {
        String batchId = UUID.randomUUID().toString();
        replayMessage(messageId, targetDestination, dryRun, performedBy, batchId);
        if (!dryRun) {
            webSocketService.notifyReplayComplete(batchId, 1);
        }
        return batchId;
    }

    // Internal overload: accepts a batchId so a group replay can thread its OWN id through every message.
    @Transactional
    public void replayMessage(String messageId, String targetDestination, boolean dryRun,
                              String performedBy, String batchId) {

        //step 1. load message from DB
        DlqMessage message = dlqMessageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message Not Found! " + messageId));

        if (dryRun) {
            log.info("[DRY-RUN] Would replay message {} to {}", messageId, targetDestination);
            saveAuditLog(batchId, message, "DRY_RUN", targetDestination, performedBy, "SUCCESS", null);
            return;
        }

        //step 2. find the brokerType
        BrokerAdapter adapter = adapters.stream()
                .filter(a -> a.getBrokerType().equals(message.getBrokerType().name()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No adapter for broker: " + message.getBrokerType()));

        try {
            //step 3 - Republish to broker FIRST. We only record an outcome once we know it.
            adapter.republishMessage(message, targetDestination);

            //step 4 - update message status
            message.setStatus(MessageStatus.REPLAYED);
            dlqMessageRepository.save(message);

            //step 5 - write the audit log AFTER a confirmed successful publish
            saveAuditLog(batchId, message, "REPLAY", targetDestination, performedBy, "SUCCESS", null);

            log.info("Replayed message {} to {} by {}", messageId, targetDestination, performedBy);

        } catch (Exception e) {
            saveAuditLog(batchId, message, "REPLAY", targetDestination, performedBy, "FAILED", e.getMessage());
            log.error("Replay failed for message: {}", messageId, e);
            throw new RuntimeException("Replay failed: " + e.getMessage());
        }
    }


    @Transactional
    public String replayGroup(String groupKey, String targetDestination, boolean dryRun, String performedBy) {

        List<DlqMessage> messages = dlqMessageRepository.findByGroupKey(groupKey);

        String batchId = UUID.randomUUID().toString();
        int replayedCount = 0;

        log.info("Replaying group {} — {} messages — dryRun={}", groupKey, messages.size(), dryRun);

        for (DlqMessage message : messages) {
            if (message.getStatus() == MessageStatus.REPLAYED) continue;
            try {
                // Pass the GROUP's batchId down so every audit row shares it and the batch is traceable.
                replayMessage(message.getId(), targetDestination, dryRun, performedBy, batchId);
                replayedCount++;
            } catch (Exception e) {
                log.error("Failed to replay message {} in group {}: {}", message.getId(), groupKey, e.getMessage());
            }
        }

        if (!dryRun && replayedCount > 0) {
            webSocketService.notifyReplayComplete(batchId, replayedCount);
        }

        return batchId;
    }

    @Transactional
    public void discardMessage(String messageId, String performedBy) {
        DlqMessage message = dlqMessageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found: " + messageId));

        message.setStatus(MessageStatus.DISCARDED);
        dlqMessageRepository.save(message);

        saveAuditLog(UUID.randomUUID().toString(), message, "DISCARD",
                null, performedBy, "SUCCESS", null);

        log.info("Message {} discarded by {}", messageId, performedBy);
    }

    private void saveAuditLog(String batchId, DlqMessage message, String action,
                              String targetDestination, String performedBy,
                              String result, String errorDetail) {

        ReplayAuditLog auditLog = ReplayAuditLog.builder()
                .batchId(batchId)
                .message(message)
                .action(action)
                .targetDestination(targetDestination)
                .performedBy(performedBy)
                .result(result)
                .errorDetail(errorDetail)
                .performedAt(LocalDateTime.now())
                .build();
        replayAuditLogRepository.save(auditLog);
    }

}