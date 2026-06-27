package com.dlqanalyzer.dlq_analyzer.adapter.rabbitmq;

import com.dlqanalyzer.dlq_analyzer.adapter.BrokerAdapter;
import com.dlqanalyzer.dlq_analyzer.model.BrokerType;
import com.dlqanalyzer.dlq_analyzer.model.DlqMessage;
import com.dlqanalyzer.dlq_analyzer.model.MessageStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RabbitMQ implementation of {@link BrokerAdapter}.
 *
 * <h2>Acknowledgement model (read this before changing pollMessage)</h2>
 * Polling uses {@link RabbitTemplate#receive(String, long)} which performs a {@code basicGet}
 * and acks the message as part of the receive. That means a polled message is REMOVED from the
 * DLQ as soon as it is read. To avoid losing a message whose conversion fails, we re-publish the
 * raw message back to the same queue (best-effort requeue) inside {@link #pollMessage}.
 *
 * NOTE: a message can still be lost if it is read here successfully but the DB persist in
 * {@code DlqIngestionService} fails. Closing that window requires switching ingestion to a
 * listener container with MANUAL acks (acking only after a committed DB write). That is a larger
 * change tracked as a follow-up; {@link #acknowledgeMessage} is the hook for it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitMQAdapter implements BrokerAdapter {

    private static final long RECEIVE_TIMEOUT_MS = 500L;

    private final RabbitTemplate rabbitTemplate;
    private final RabbitAdmin rabbitAdmin;
    private final ObjectMapper objectMapper;

    @Value("${dlq.rabbitmq.dlq-patterns:.dlq,-dlq,.dead-letter}")
    private String dlqPatterns;

    @Value("${dlq.rabbitmq.dlq-names:}")
    private String dlqNames;

    @Value("${dlq.rabbitmq.management-url:http://localhost:15672}")
    private String managementUrl;

    @Value("${dlq.rabbitmq.management-vhost:/}")
    private String managementVhost;

    @Value("${spring.rabbitmq.username:guest}")
    private String rabbitUser;

    @Value("${spring.rabbitmq.password:guest}")
    private String rabbitPassword;

    private RestClient managementClient;

    @Override
    public List<DlqMessage> pollMessage(String destination, int limit) {
        List<DlqMessage> messages = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            Message raw = rabbitTemplate.receive(destination, RECEIVE_TIMEOUT_MS);
            if (raw == null) break;
            try {
                messages.add(convertToDlqMessage(raw, destination));
            } catch (Exception e) {
                // The message was already acked on receive. Don't drop it: put it back.
                log.error("Failed to convert a message from {}; requeuing to avoid data loss: {}",
                        destination, e.getMessage());
                try {
                    rabbitTemplate.send(destination, raw);
                } catch (Exception requeueEx) {
                    log.error("Requeue to {} FAILED; this message may be lost: {}",
                            destination, requeueEx.getMessage());
                }
            }
        }
        log.info("Polled {} message(s) from {}", messages.size(), destination);
        return messages;
    }

    @Override
    public void republishMessage(DlqMessage message, String targetDestination) {
        byte[] body = message.getPayload() == null
                ? new byte[0]
                : message.getPayload().getBytes(StandardCharsets.UTF_8);

        Map<String, Object> stored = deserializeHeaders(message.getHeaders());
        Object contentType = stored.remove("__contentType");

        MessageProperties props = new MessageProperties();
        props.setContentType(contentType != null
                ? contentType.toString()
                : MessageProperties.CONTENT_TYPE_JSON);
        if (message.getMessageId() != null) {
            props.setMessageId(message.getMessageId());
        }
        // Re-apply the original application headers (x-death / x-exception-* were stripped on ingest).
        stored.forEach(props::setHeader);

        // 2-arg send() publishes to the default exchange with targetDestination as the routing key,
        // i.e. straight to the queue named targetDestination — same routing as the original code,
        // but now carrying the payload bytes, content type, messageId and original headers.
        rabbitTemplate.send(targetDestination, new Message(body, props));
        log.info("Replayed message {} to {} ({} bytes)", message.getMessageId(), targetDestination, body.length);
    }

    @Override
    public void acknowledgeMessage(DlqMessage message) {
        // Intentionally a no-op for the current poll-based model: receive() already acks.
        // This becomes meaningful when ingestion moves to a manual-ack listener container
        // (see class javadoc). Kept on the interface so that change is non-breaking.
    }

    @Override
    public List<String> listDlqDestinations() {
        // 1. Manual override wins.
        if (dlqNames != null && !dlqNames.trim().isEmpty()) {
            List<String> manual = Arrays.stream(dlqNames.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            log.info("Using manually configured DLQ names: {}", manual);
            return manual;
        }

        // 2. Auto-discover via the management API, filter by suffix patterns.
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

            log.info("Auto-discovered {} DLQ(s) matching {}: {}", discovered.size(), patterns, discovered);
            return discovered;
        } catch (Exception e) {
            log.warn("DLQ auto-discovery failed ({}). Returning empty list; will retry next poll.",
                    e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> fetchAllQueueNames() {
        RestClient client = managementClient();
        List<Map<String, Object>> queues = client.get()
                .uri("/api/queues")
                .retrieve()
                .body(List.class);

        if (queues == null) {
            return List.of();
        }

        return queues.stream()
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
                    .encodeToString((rabbitUser + ":" + rabbitPassword).getBytes(StandardCharsets.UTF_8));
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

    // ------------------------------------------------------------------ //
    //  Conversion                                                        //
    // ------------------------------------------------------------------ //

    private DlqMessage convertToDlqMessage(Message raw, String destination) {
        MessageProperties props = raw.getMessageProperties();
        Map<String, Object> headers = props.getHeaders() != null ? props.getHeaders() : Map.of();
        String payload = new String(raw.getBody(), StandardCharsets.UTF_8);

        XDeath xDeath = parseXDeath(headers);
        String originalQueue = (xDeath != null && xDeath.queue != null) ? xDeath.queue : destination;

        DlqMessage msg = new DlqMessage();
        msg.setBrokerType(BrokerType.RABBITMQ);
        msg.setSourceDestination(destination);
        msg.setPayload(payload);
        msg.setStatus(MessageStatus.PENDING);

        // failure_count / first_failed_at now come from x-death when available instead of being hard-coded.
        msg.setFailureCount(xDeath != null && xDeath.count > 0 ? (int) Math.min(xDeath.count, Integer.MAX_VALUE) : 1);
        msg.setFirstFailedAt(xDeath != null && xDeath.time != null
                ? LocalDateTime.ofInstant(xDeath.time.toInstant(), ZoneId.systemDefault())
                : LocalDateTime.now());
        msg.setLastFailedAt(LocalDateTime.now());
        msg.setCreatedAt(LocalDateTime.now());

        // Error metadata: prefer explicit x-exception-* headers, fall back to x-death reason.
        msg.setStackTrace(asString(headers.get("x-exception-stacktrace")));
        msg.setErrorClass(asString(firstNonNull(headers.get("x-exception-type"),
                xDeath != null ? xDeath.reason : null)));
        msg.setErrorMessage(asString(firstNonNull(headers.get("x-exception-message"),
                xDeath != null ? ("dead-lettered: " + xDeath.reason) : null)));

        // Stable, deterministic id so DlqIngestionService dedup actually works
        // (was UUID.randomUUID() on every poll, which made existsByMessageId() always false).
        msg.setMessageId(resolveMessageId(props, originalQueue, payload, xDeath));

        // Preserve headers + content type so replay is faithful (column existed but was never set).
        msg.setHeaders(serializeHeaders(props));

        return msg;
    }

    private String resolveMessageId(MessageProperties props, String originalQueue, String payload, XDeath xDeath) {
        if (props.getMessageId() != null && !props.getMessageId().isBlank()) {
            return props.getMessageId();
        }
        if (props.getCorrelationId() != null && !props.getCorrelationId().isBlank()) {
            return props.getCorrelationId();
        }
        // Deterministic fingerprint so the same physical message dedupes if it is re-read.
        String basis = originalQueue + "|" + payload + "|"
                + (xDeath != null ? xDeath.count + "|" + xDeath.time : "");
        return "sha256:" + sha256(basis);
    }

    @SuppressWarnings("unchecked")
    private XDeath parseXDeath(Map<String, Object> headers) {
        Object raw = headers.get("x-death");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        Object first = list.get(0);
        if (!(first instanceof Map<?, ?> m)) {
            return null;
        }
        XDeath x = new XDeath();
        Object count = m.get("count");
        if (count instanceof Number n) {
            x.count = n.longValue();
        }
        x.reason = Objects.toString(m.get("reason"), null);
        x.queue = Objects.toString(m.get("queue"), null);
        Object time = m.get("time");
        if (time instanceof Date d) {
            x.time = d;
        }
        return x;
    }

    private String serializeHeaders(MessageProperties props) {
        try {
            Map<String, Object> out = new LinkedHashMap<>();
            if (props.getContentType() != null) {
                out.put("__contentType", props.getContentType());
            }
            Map<String, Object> h = props.getHeaders();
            if (h != null) {
                h.forEach((k, v) -> {
                    if (k != null && !k.startsWith("x-death") && !k.startsWith("x-exception")) {
                        out.put(k, v == null ? null : v.toString());
                    }
                });
            }
            return out.isEmpty() ? null : objectMapper.writeValueAsString(out);
        } catch (Exception e) {
            log.warn("Failed to serialize headers: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deserializeHeaders(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, LinkedHashMap.class);
        } catch (Exception e) {
            log.warn("Failed to deserialize stored headers, replaying without them: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }

    private static Object firstNonNull(Object a, Object b) {
        return a != null ? a : b;
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return "fallback-" + Math.abs(input.hashCode());
        }
    }

    /** Minimal view of the first entry of RabbitMQ's {@code x-death} header. */
    private static final class XDeath {
        long count;
        String reason;
        String queue;
        Date time;
    }
}