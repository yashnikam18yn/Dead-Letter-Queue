package com.dlqanalyzer.dlq_analyzer.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "replay_audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReplayAuditLog {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "batch_id", nullable = false, length = 36)
    private String batchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private DlqMessage message;

    @Column(nullable = false, length = 20)
    private String action;

    @Column(name = "target_destination")
    private String targetDestination;

    @Column(name = "performed_by", nullable = false)
    private String performedBy;

    @Column(nullable = false, length = 20)
    private String result;

    @Column(name = "error_detail", columnDefinition = "TEXT")
    private String errorDetail;

    @Column(name = "performed_at", nullable = false)
    private LocalDateTime performedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (performedAt == null) performedAt = LocalDateTime.now();
    }
}