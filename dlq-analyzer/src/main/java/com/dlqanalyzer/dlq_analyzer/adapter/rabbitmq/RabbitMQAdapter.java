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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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

    // RabbitMQ Management REST API settings (used for auto-discovery)
    @Value("${dlq.rabbitmq.management-url:http://localhost:15672}")
    private String managementUrl;

    @Value("${dlq.rabbitmq.management-vhost:/}")
    private String managementVhost;

    @Value("${spring.rabbitmq.username:guest}")
    private String rabbitUser;

    @Value("${spring.rabbitmq.password:guest}")
    private String rabbitPassword;

    // Built lazily so a momentary RabbitMQ outage at startup doesn't break the bean.
    private RestClient managementClient;

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

    /**
     * Returns the list of DLQ destinations to poll.
     *
     * Order of precedence:
     *   1. If dlq-names is explicitly set (non-empty), use that exact list (manual override).
     *   2. Otherwise, auto-discover via the RabbitMQ Management API and keep only the
     *      queues whose name matches one of the configured dlq-patterns (suffix match).
     *
     * Any failure during discovery is logged and an empty list is returned so the
     * scheduled poller keeps running instead of crashing.
     */
    @Override
    public List<String> listDlqDestinations() {
        // 1. Manual override (fixed the original ||/empty bug here).
        if (dlqNames != null && !dlqNames.trim().isEmpty()) {
            List<String> manual = Arrays.stream(dlqNames.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            log.info("Using manually configured DLQ names: {}", manual);
            return manual;
        }

        // 2. Auto-discover from the management API.
        try {
            List<String> allQueues = fetchAllQueueNames();
            List<String> patterns = Arrays.stream(dlqPatterns.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());

            List<String> discovered = allQueues.stream()
                    .filter(name -> patterns.stream().anyMatch(name::endsWith))
                    .distinct()
                    .collect(Collectors.toList());

            log.info("Auto-discovered {} DLQ(s) matching {}: {}",
                    discovered.size(), patterns, discovered);
            return discovered;
        } catch (Exception e) {
            log.warn("DLQ auto-discovery failed ({}). Returning empty list; will retry next poll.",
                    e.getMessage());
            return List.of();
        }
    }

    /**
     * Calls GET {managementUrl}/api/queues/{vhost} and extracts the "name" of every queue.
     * Uses HTTP basic auth with the same RabbitMQ credentials.
     */
    @SuppressWarnings("unchecked")
    private List<String> fetchAllQueueNames() {
        RestClient client = managementClient();

        // Use the all-vhosts endpoint (/api/queues) instead of /api/queues/{vhost}.
        // This avoids the RestClient double-encoding of the "%2F" vhost (which caused
        // a 404 "Object Not Found"), and naturally discovers queues across every vhost.
        // We then optionally filter by the configured vhost if one other than "/" is set.
        List<Map<String, Object>> queues = client.get()
                .uri("/api/queues")
                .retrieve()
                .body(List.class);

        if (queues == null) {
            return List.of();
        }

        return queues.stream()
                // If a specific vhost is configured, keep only queues in that vhost.
                .filter(q -> managementVhost == null
                        || managementVhost.isEmpty()
                        || managementVhost.equals(Objects.toString(q.get("vhost"), "/")))
                .map(q -> Objects.toString(q.get("name"), null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private RestClient managementClient() {
        if (managementClient == null) {
            String basicAuth = Base64.getEncoder()
                    .encodeToString((rabbitUser + ":" + rabbitPassword)
                            .getBytes(StandardCharsets.UTF_8));
            managementClient = RestClient.builder()
                    .baseUrl(managementUrl)
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth)
                    .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .build();
        }
        return managementClient;
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