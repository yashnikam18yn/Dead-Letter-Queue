package com.dlqanalyzer.dlq_analyzer.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketService {

    private final SimpMessagingTemplate messagingTemplate;

    // Called when new DLQ messages are found
    public void notifyNewMessages(long totalPending){
        messagingTemplate.convertAndSend("/topic/dlq-updates", Map.of("totalPending", totalPending, "event", "NEW_MESSAGES"));
        log.info("WebSocket push — new messages, total pending: {}", totalPending);
    }

    public void notifyReplayComplete(String batchId, int count){
        messagingTemplate.convertAndSend("/topic/replay-status", Map.of("batchId", batchId, "count", count, "event", "REPLAY_COMPLETE"));
        log.info("WebSocket push — replay complete, batchId: {}", batchId);
    }
}
