package com.dlqanalyzer.dlq_analyzer.service;

import com.dlqanalyzer.dlq_analyzer.adapter.BrokerAdapter;
import com.dlqanalyzer.dlq_analyzer.model.BrokerType;
import com.dlqanalyzer.dlq_analyzer.model.DlqMessage;
import com.dlqanalyzer.dlq_analyzer.model.MessageStatus;
import com.dlqanalyzer.dlq_analyzer.model.ReplayAuditLog;
import com.dlqanalyzer.dlq_analyzer.repository.DlqMessageRepository;
import com.dlqanalyzer.dlq_analyzer.repository.ReplayAuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ReplayEngineTest {

    private BrokerAdapter adapter;
    private DlqMessageRepository dlqMessageRepository;
    private ReplayAuditLogRepository auditLogRepository;
    private WebSocketService webSocketService;
    private ReplayEngine replayEngine;

    @BeforeEach
    void setUp() {
        adapter = mock(BrokerAdapter.class);
        dlqMessageRepository = mock(DlqMessageRepository.class);
        auditLogRepository = mock(ReplayAuditLogRepository.class);
        webSocketService = mock(WebSocketService.class);

        // Adapter advertises RABBITMQ so it matches a RABBITMQ message.
        when(adapter.getBrokerType()).thenReturn(BrokerType.RABBITMQ.name());

        replayEngine = new ReplayEngine(
                List.of(adapter), dlqMessageRepository, auditLogRepository, webSocketService);
    }

    private DlqMessage pendingMessage(String id) {
        DlqMessage m = new DlqMessage();
        m.setId(id);
        m.setMessageId(id);
        m.setBrokerType(BrokerType.RABBITMQ);
        m.setStatus(MessageStatus.PENDING);
        m.setPayload("{\"some\":\"payload\"}");
        return m;
    }

    // ---- success path --------------------------------------------------

    @Test
    @DisplayName("Successful replay publishes, marks REPLAYED, and writes ONE SUCCESS audit row with action REPLAY")
    void successfulReplay_writesSingleSuccessRow() {
        DlqMessage msg = pendingMessage("m1");
        when(dlqMessageRepository.findById("m1")).thenReturn(Optional.of(msg));

        String batchId = replayEngine.replayMessage("m1", "orders.retry", false, "tester");

        // published exactly once
        verify(adapter, times(1)).republishMessage(msg, "orders.retry");
        // status flipped to REPLAYED and saved
        assertThat(msg.getStatus()).isEqualTo(MessageStatus.REPLAYED);
        verify(dlqMessageRepository, times(1)).save(msg);

        // exactly one audit row, and it is SUCCESS with the correct action string
        ArgumentCaptor<ReplayAuditLog> captor = ArgumentCaptor.forClass(ReplayAuditLog.class);
        verify(auditLogRepository, times(1)).save(captor.capture());
        ReplayAuditLog row = captor.getValue();
        assertThat(row.getResult()).isEqualTo("SUCCESS");
        assertThat(row.getAction()).isEqualTo("REPLAY");   // not the old "REPLY" typo
        assertThat(row.getBatchId()).isEqualTo(batchId);

        assertThat(batchId).isNotBlank();
    }

    // ---- failure path: this is where the worst Phase 1 bug lived -------

    @Test
    @DisplayName("Failed publish writes ONE FAILED row, does NOT mark REPLAYED, and propagates the error")
    void failedPublish_writesSingleFailedRow_noSuccess() {
        DlqMessage msg = pendingMessage("m2");
        when(dlqMessageRepository.findById("m2")).thenReturn(Optional.of(msg));
        doThrow(new RuntimeException("broker down"))
                .when(adapter).republishMessage(any(DlqMessage.class), anyString());

        assertThatThrownBy(() -> replayEngine.replayMessage("m2", "orders.retry", false, "tester"))
                .isInstanceOf(RuntimeException.class);

        // status must NOT have been changed to REPLAYED
        assertThat(msg.getStatus()).isEqualTo(MessageStatus.PENDING);
        verify(dlqMessageRepository, never()).save(any());

        // exactly one audit row, and it is FAILED (no phantom SUCCESS row)
        ArgumentCaptor<ReplayAuditLog> captor = ArgumentCaptor.forClass(ReplayAuditLog.class);
        verify(auditLogRepository, times(1)).save(captor.capture());
        ReplayAuditLog row = captor.getValue();
        assertThat(row.getResult()).isEqualTo("FAILED");
        assertThat(row.getAction()).isEqualTo("REPLAY");

        // no success notification on a failure
        verify(webSocketService, never()).notifyReplayComplete(anyString(), anyInt());
    }

    // ---- dry run -------------------------------------------------------

    @Test
    @DisplayName("Dry run publishes nothing and writes a DRY_RUN audit row")
    void dryRun_doesNotPublish() {
        DlqMessage msg = pendingMessage("m3");
        when(dlqMessageRepository.findById("m3")).thenReturn(Optional.of(msg));

        replayEngine.replayMessage("m3", "orders.retry", true, "tester");

        verify(adapter, never()).republishMessage(any(), anyString());
        verify(dlqMessageRepository, never()).save(any());

        ArgumentCaptor<ReplayAuditLog> captor = ArgumentCaptor.forClass(ReplayAuditLog.class);
        verify(auditLogRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo("DRY_RUN");

        // dry run does not notify replay-complete
        verify(webSocketService, never()).notifyReplayComplete(anyString(), anyInt());
    }

    // ---- group batchId propagation (Phase 1 fix #7) --------------------

    @Test
    @DisplayName("Group replay threads ONE batchId through every message's audit row")
    void groupReplay_sharesSingleBatchId() {
        DlqMessage a = pendingMessage("g1");
        DlqMessage b = pendingMessage("g2");
        when(dlqMessageRepository.findByGroupKey("grp")).thenReturn(List.of(a, b));
        when(dlqMessageRepository.findById("g1")).thenReturn(Optional.of(a));
        when(dlqMessageRepository.findById("g2")).thenReturn(Optional.of(b));

        String groupBatchId = replayEngine.replayGroup("grp", "orders.retry", false, "tester");

        // every saved audit row must carry the SAME group batchId
        ArgumentCaptor<ReplayAuditLog> captor = ArgumentCaptor.forClass(ReplayAuditLog.class);
        verify(auditLogRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(ReplayAuditLog::getBatchId)
                .containsOnly(groupBatchId);

        // notified once for the batch, with the count of replayed messages
        verify(webSocketService, times(1)).notifyReplayComplete(groupBatchId, 2);
    }

    @Test
    @DisplayName("Group replay skips messages already REPLAYED")
    void groupReplay_skipsAlreadyReplayed() {
        DlqMessage a = pendingMessage("g1");
        DlqMessage done = pendingMessage("g2");
        done.setStatus(MessageStatus.REPLAYED);
        when(dlqMessageRepository.findByGroupKey("grp")).thenReturn(List.of(a, done));
        when(dlqMessageRepository.findById("g1")).thenReturn(Optional.of(a));

        replayEngine.replayGroup("grp", "orders.retry", false, "tester");

        // only the pending one was published
        verify(adapter, times(1)).republishMessage(any(DlqMessage.class), anyString());
        verify(adapter, times(1)).republishMessage(eq(a), anyString());
    }
}