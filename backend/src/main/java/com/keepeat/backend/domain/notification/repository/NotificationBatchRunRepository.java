package com.keepeat.backend.domain.notification.repository;

import com.keepeat.backend.domain.notification.entity.NotificationBatchRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

public interface NotificationBatchRunRepository extends JpaRepository<NotificationBatchRun, LocalDate> {

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO notification_batch_run (run_date, started_at) VALUES (:runDate, :now)
            ON CONFLICT (run_date) DO UPDATE SET started_at = EXCLUDED.started_at, completed_at = NULL
            """, nativeQuery = true)
    void markStarted(@Param("runDate") LocalDate runDate, @Param("now") Instant now);

    @Modifying
    @Transactional
    @Query("UPDATE NotificationBatchRun r SET r.completedAt = :now WHERE r.runDate = :runDate")
    void markCompleted(@Param("runDate") LocalDate runDate, @Param("now") Instant now);

    boolean existsByRunDateAndCompletedAtIsNull(LocalDate runDate);
}
