package com.dlqanalyzer.dlq_analyzer.adapter.rabbitmq;
import com.dlqanalyzer.dlq_analyzer.model.BrokerType;
import com.dlqanalyzer.dlq_analyzer.model.MessageStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Value;
import com.dlqanalyzer.dlq_analyzer.adapter.BrokerAdapter;
import com.dlqanalyzer.dlq_analyzer.model.DlqMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitMQAdapter implements BrokerAdapter {

    private final RabbitTemplate rabbitTemplate;
    private final RabbitAdmin rabbitAdmin;

    @Value("${dlq.rabbitmq.dlq-patterns:.dlq,-dlq,.dead-letter}")
    private String dlqPatterns;

    @Value("${dlq.rabbitmq.dlq-names:}")
    private String dlqNames;

    @Override
    public List<DlqMessage> pollMessage(String destination, int limit) {
        List<DlqMessage> messages = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            Message raw = rabbitTemplate.receive(destination, 1000);
            if (raw == null) break;
            messages.add(convertToDlqMessage(raw, destination));
        }
        log.info("Polled {} messages from {}", messages.size(), destination);
        return messages;
    }

    @Override
    public void republishMessage(DlqMessage message, String targetDestination) {
        rabbitTemplate.convertAndSend(targetDestination, message.getPayload());
        log.info("Replayed message {} to {}", message.getMessageId(), targetDestination);
    }

    @Override
    public void acknowledgeMessage(DlqMessage message) {
        // This one we need later while we integrate the kafka
    }

    @Override
    public List<String> listDlqDestinations() {
        if(dlqNames != null || !dlqNames.isEmpty()){
            return Arrays.asList(dlqNames.split(","));
        }
        return List.of();
    }

    @Override
    public String getBrokerType() {
        return BrokerType.RABBITMQ.name();
    }

    private DlqMessage convertToDlqMessage(Message raw, String destination) {
        DlqMessage msg = new DlqMessage();
        msg.setId(UUID.randomUUID().toString());
        msg.setMessageId(UUID.randomUUID().toString());
        msg.setBrokerType(BrokerType.RABBITMQ);
        msg.setSourceDestination(destination);
        msg.setPayload(new String(raw.getBody()));
        msg.setStatus(MessageStatus.PENDING);
        msg.setFailureCount(1);
        msg.setFirstFailedAt(LocalDateTime.now());
        msg.setLastFailedAt(LocalDateTime.now());
        msg.setCreatedAt(LocalDateTime.now());

        Map<String, Object> headers = raw.getMessageProperties().getHeaders();
        if (headers.containsKey("x-exception-stacktrace")) {
            msg.setStackTrace(headers.get("x-exception-stacktrace").toString());
        }
        if (headers.containsKey("x-exception-type")) {
            msg.setErrorClass(headers.get("x-exception-type").toString());
        }
        if (headers.containsKey("x-exception-message")) {
            msg.setErrorMessage(headers.get("x-exception-message").toString());
        }
        return msg;
    }
}
