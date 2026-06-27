package com.dlqanalyzer.dlq_analyzer.service;

import com.dlqanalyzer.dlq_analyzer.adapter.BrokerAdapter;
import com.dlqanalyzer.dlq_analyzer.model.DlqMessage;
import com.dlqanalyzer.dlq_analyzer.model.MessageStatus;
import com.dlqanalyzer.dlq_analyzer.repository.DlqMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DlqIngestionService {

    private final List<BrokerAdapter> adapters;
    private final DlqMessageRepository dlqMessageRepository;
    private final ErrorClassifier errorClassifier;
    private final WebSocketService webSocketService;

    // Lets tests (and any environment without brokers) turn the scheduled poller off.
    @Value("${dlq.polling.enabled:true}")
    private boolean pollingEnabled;

    @Scheduled(fixedDelayString = "${dlq.polling.interval-ms:30000}")
    public void poll() {
        if (!pollingEnabled) {
            return;
        }

        log.info("DLQ polling Started...");
        int newlySaved = 0;

        for (BrokerAdapter adapter : adapters) {
            List<String> destinations = adapter.listDlqDestinations();
            for (String destination : destinations) {
                try {
                    List<DlqMessage> messages = adapter.pollMessage(destination, 100);
                    for (DlqMessage message : messages) {
                        Optional<DlqMessage> existing =
                                dlqMessageRepository.findByMessageId(message.getMessageId());

                        if (existing.isEmpty()) {
                            String groupKey = errorClassifier.classify(message);
                            message.setGroupKey(groupKey);
                            dlqMessageRepository.save(message);
                            newlySaved++;
                            log.info("Saved new DLQ message from {}", destination);
                        } else {
                            // Same message seen again: aggregate instead of silently dropping it,
                            // so failure_count / last_failed_at reflect reality.
                            DlqMessage row = existing.get();
                            row.setFailureCount(row.getFailureCount() + 1);
                            row.setLastFailedAt(LocalDateTime.now());
                            dlqMessageRepository.save(row);
                            log.debug("Updated failure count for existing message {} from {}",
                                    row.getMessageId(), destination);
                        }
                    }
                } catch (Exception e) {
                    log.error("Error polling destination {}: {}", destination, e.getMessage());
                }
            }
        }

        if (newlySaved > 0) {
            long totalPending = dlqMessageRepository.findByStatus(MessageStatus.PENDING).size();
            webSocketService.notifyNewMessages(totalPending);
        }

        log.info("DLQ polling completed. {} new message(s) ingested.", newlySaved);
    }
}