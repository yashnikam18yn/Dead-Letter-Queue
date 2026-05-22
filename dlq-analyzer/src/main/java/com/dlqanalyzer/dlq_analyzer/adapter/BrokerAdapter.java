package com.dlqanalyzer.dlq_analyzer.adapter;

import com.dlqanalyzer.dlq_analyzer.model.DlqMessage;

import java.util.List;

public interface BrokerAdapter {
    List<DlqMessage> pollMessage(String destination, int limit);
    void republishMessage(DlqMessage message, String targetDestination);
    void acknowledgeMessage(DlqMessage message);
    List<String> listDlqDestinations();
    String getBrokerType();
}
