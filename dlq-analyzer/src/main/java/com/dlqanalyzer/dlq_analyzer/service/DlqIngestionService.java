package com.dlqanalyzer.dlq_analyzer.service;

import com.dlqanalyzer.dlq_analyzer.adapter.BrokerAdapter;
import com.dlqanalyzer.dlq_analyzer.model.DlqMessage;
import com.dlqanalyzer.dlq_analyzer.model.MessageStatus;
import com.dlqanalyzer.dlq_analyzer.repository.DlqMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DlqIngestionService {


    private final List<BrokerAdapter> adapters;
    private final DlqMessageRepository dlqMessageRepository;

    private final ErrorClassifier errorClassifier;

    private final WebSocketService webSocketService;

    @Scheduled(fixedDelayString = "${dlq.polling.interval-ms:30000}")
    public void poll(){
        log.info("DLQ polling Started...");
        int newlySaved = 0;

        for(BrokerAdapter adapter: adapters){
            List<String> destinations = adapter.listDlqDestinations();
            for(String destination: destinations){
                try{
                    List<DlqMessage> messages = adapter.pollMessage(destination, 100);
                    for (DlqMessage message: messages){
                        if(!dlqMessageRepository.existsByMessageId(message.getMessageId())){
                            String groupKey = errorClassifier.classify(message);
                            message.setGroupKey(groupKey);
                            dlqMessageRepository.save(message);
                            newlySaved++;
                            log.info("Saved new DLQ message from {}", destination);
                        }
                    }
                }catch (Exception e){
                    log.error("Error polling destination {}: {}", destination, e.getMessage());
                }
            }
        }

        // Notify ONCE per poll cycle, and only if something actually changed.
        if (newlySaved > 0) {
            long totalPending = dlqMessageRepository.findByStatus(MessageStatus.PENDING).size();
            webSocketService.notifyNewMessages(totalPending);
        }

        log.info("DLQ polling completed. {} new message(s) ingested.", newlySaved);
    }

}