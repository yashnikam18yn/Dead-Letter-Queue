package com.dlqanalyzer.dlq_analyzer.controller;


import com.dlqanalyzer.dlq_analyzer.model.MessageStatus;
import com.dlqanalyzer.dlq_analyzer.repository.DlqMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/stats")
@RequiredArgsConstructor
public class StatsController {

    private final DlqMessageRepository dlqMessageRepository;

    @GetMapping
    public Map<String, Object> getStats() {
        long total = dlqMessageRepository.count();
        long pending = dlqMessageRepository.findByStatus(MessageStatus.PENDING).size();
        long replayed = dlqMessageRepository.findByStatus(MessageStatus.REPLAYED).size();
        long discarded = dlqMessageRepository.findByStatus(MessageStatus.DISCARDED).size();

        return Map.of(
                "total", total,
                "pending", pending,
                "replayed", replayed,
                "discarded", discarded
        );
    }

}
