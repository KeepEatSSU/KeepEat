package com.keepeat.backend.domain.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "notification_batch_run")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationBatchRun {

    @Id
    @Column(name = "run_date")
    private LocalDate runDate;

    @Column(name = "started_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant startedAt;

    @Column(name = "completed_at", columnDefinition = "timestamp with time zone")
    private Instant completedAt;
}
