package com.dlqanalyzer.dlq_analyzer.adapter.rabbitmq;

import com.dlqanalyzer.dlq_analyzer.model.DlqMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RabbitMQAdapterTest {

    private RabbitTemplate rabbitTemplate;
    private RabbitMQAdapter adapter;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        RabbitAdmin rabbitAdmin = mock(RabbitAdmin.class);
        adapter = new RabbitMQAdapter(rabbitTemplate, rabbitAdmin, new ObjectMapper());
        // @Value fields are not injected in a plain unit test; set the ones we exercise.
        ReflectionTestUtils.setField(adapter, "dlqNames", "");
        ReflectionTestUtils.setField(adapter, "dlqPatterns", ".dlq,-dlq");
    }

    private Message message(byte[] body, MessageProperties props) {
        return new Message(body, props);
    }

    @Test
    @DisplayName("poll: parses x-death for failure count and original queue, and produces a stable id")
    void poll_parsesXDeath_andStableId() {
        MessageProperties props = new MessageProperties();
        props.setHeader("x-death", List.of(Map.of(
                "count", 3L,
                "reason", "rejected",
                "queue", "orders",
                "time", new Date(0)
        )));
        Message raw = message("{\"order\":1}".getBytes(StandardCharsets.UTF_8), props);

        // First receive returns the message, second returns null to end the loop.
        when(rabbitTemplate.receive(eq("orders.dlq"), anyLong()))
                .thenReturn(raw)
                .thenReturn(null);

        List<DlqMessage> result = adapter.pollMessage("orders.dlq", 10);

        assertThat(result).hasSize(1);
        DlqMessage m = result.get(0);
        assertThat(m.getFailureCount()).isEqualTo(3);
        assertThat(m.getErrorClass()).isEqualTo("rejected");
        assertThat(m.getMessageId()).startsWith("sha256:"); // deterministic, not a random UUID
        assertThat(m.getSourceDestination()).isEqualTo("orders.dlq");
    }

    @Test
    @DisplayName("poll: identical payloads produce identical ids (dedup actually works now)")
    void poll_sameMessage_sameId() {
        MessageProperties props = new MessageProperties();
        Message a = message("same".getBytes(StandardCharsets.UTF_8), props);
        Message b = message("same".getBytes(StandardCharsets.UTF_8), new MessageProperties());

        when(rabbitTemplate.receive(eq("q.dlq"), anyLong())).thenReturn(a).thenReturn(null);
        String id1 = adapter.pollMessage("q.dlq", 10).get(0).getMessageId();

        when(rabbitTemplate.receive(eq("q.dlq"), anyLong())).thenReturn(b).thenReturn(null);
        String id2 = adapter.pollMessage("q.dlq", 10).get(0).getMessageId();

        assertThat(id1).isEqualTo(id2);
    }

    @Test
    @DisplayName("poll: prefers an explicit message-id property over the fingerprint")
    void poll_usesMessageIdProperty() {
        MessageProperties props = new MessageProperties();
        props.setMessageId("real-id-123");
        Message raw = message("body".getBytes(StandardCharsets.UTF_8), props);
        when(rabbitTemplate.receive(eq("q.dlq"), anyLong())).thenReturn(raw).thenReturn(null);

        assertThat(adapter.pollMessage("q.dlq", 10).get(0).getMessageId()).isEqualTo("real-id-123");
    }

    @Test
    @DisplayName("poll: app headers and content type are preserved; internal headers stripped")
    void poll_preservesAppHeaders() {
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setHeader("tenant", "acme");
        props.setHeader("x-death", List.of(Map.of("count", 1L, "reason", "expired")));
        Message raw = message("{}".getBytes(StandardCharsets.UTF_8), props);
        when(rabbitTemplate.receive(eq("q.dlq"), anyLong())).thenReturn(raw).thenReturn(null);

        String headers = adapter.pollMessage("q.dlq", 10).get(0).getHeaders();

        assertThat(headers).contains("tenant").contains("acme").contains("__contentType");
        assertThat(headers).doesNotContain("x-death");
    }

    @Test
    @DisplayName("republish: sends a Message that carries the messageId and original headers")
    void republish_carriesProperties() {
        DlqMessage m = new DlqMessage();
        m.setMessageId("mid-1");
        m.setPayload("{\"k\":\"v\"}");
        m.setHeaders("{\"__contentType\":\"application/json\",\"tenant\":\"acme\"}");

        adapter.republishMessage(m, "orders.retry");

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq("orders.retry"), captor.capture());
        Message sent = captor.getValue();
        assertThat(new String(sent.getBody(), StandardCharsets.UTF_8)).isEqualTo("{\"k\":\"v\"}");
        assertThat(sent.getMessageProperties().getMessageId()).isEqualTo("mid-1");
        assertThat(sent.getMessageProperties().getHeaders()).containsEntry("tenant", "acme");
    }

    @Test
    @DisplayName("listDlqDestinations: manual override returns the configured names verbatim")
    void listDestinations_manualOverride() {
        ReflectionTestUtils.setField(adapter, "dlqNames", "orders.dlq, payments.dlq ");
        assertThat(adapter.listDlqDestinations()).containsExactly("orders.dlq", "payments.dlq");
    }
}