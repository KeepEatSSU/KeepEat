package com.keepeat.backend.domain.notification.perf;

import com.keepeat.backend.domain.notification.scheduler.IngredientExpiryScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * 소비기한 알림 배치를 원하는 시점에 실행하는 측정 전용 트리거 (#51).
 * perf 프로필에서만 빈으로 등록된다.
 */
@Profile("perf")
@RestController
@RequiredArgsConstructor
public class ExpiryBatchTriggerController {

    private final IngredientExpiryScheduler ingredientExpiryScheduler;
    private final TaskScheduler taskScheduler;

    @PostMapping("/api/internal/perf/expiry-batch")
    public ResponseEntity<Map<String, String>> trigger() {
        Instant scheduledAt = Instant.now();
        // 요청 스레드가 아니라 @Scheduled와 같은 스케줄러 스레드에서 실행해야
        // CallerRunsPolicy, receipt 확인과의 스레드 공유 같은 실제 09:00 동작이 재현된다.
        taskScheduler.schedule(ingredientExpiryScheduler::checkExpiringIngredients, scheduledAt);
        return ResponseEntity.accepted().body(Map.of("scheduledAt", scheduledAt.toString()));
    }
}
