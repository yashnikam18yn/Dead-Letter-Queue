package com.dlqanalyzer.dlq_analyzer.controller;


import com.dlqanalyzer.dlq_analyzer.model.ReplayAuditLog;
import com.dlqanalyzer.dlq_analyzer.repository.ReplayAuditLogRepository;
import com.dlqanalyzer.dlq_analyzer.service.ReplayEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/replay")
@RequiredArgsConstructor
public class ReplayController {
    private final ReplayEngine replayEngine;
    private final ReplayAuditLogRepository replayAuditLogRepository;

    @PostMapping("/message/{id}")
    public ResponseEntity<Map<String, String>> replayMessage(@PathVariable String id,
                                                             @RequestParam String targetDestination,
                                                             @RequestParam(defaultValue = "false") boolean dryRun){
        String batchId = replayEngine.replayMessage(id, targetDestination, dryRun, "admin");
        return ResponseEntity.ok(Map.of(
                "batchId", batchId,
                "status", dryRun ? "DRY_RUN" : "REPLAYED"
        ));
    }

    // Replay entire error group
    @PostMapping("/group/{groupKey}")
    public ResponseEntity<Map<String, String>> replayGroup(
            @PathVariable String groupKey,
            @RequestParam String targetDestination,
            @RequestParam(defaultValue = "false") boolean dryRun) {

        String batchId = replayEngine.replayGroup(groupKey, targetDestination, dryRun, "admin");
        return ResponseEntity.ok(Map.of(
                "batchId", batchId,
                "status", dryRun ? "DRY_RUN" : "REPLAYED"
        ));
    }

    // Discard a message
    @PostMapping("/discard/{id}")
    public ResponseEntity<Void> discard(@PathVariable String id) {
        replayEngine.discardMessage(id, "admin");
        return ResponseEntity.ok().build();
    }

    // Get audit log
    @GetMapping("/audit")
    public List<ReplayAuditLog> getAuditLog() {
        return replayAuditLogRepository.findAll();
    }

    // Get audit log by batch
    @GetMapping("/audit/batch/{batchId}")
    public List<ReplayAuditLog> getByBatch(@PathVariable String batchId) {
        return replayAuditLogRepository.findByBatchId(batchId);
    }
}
