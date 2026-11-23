package com.keepeat.backend.domain.notification.scheduler;

import com.keepeat.backend.domain.notification.repository.NotificationBatchRunRepository;
import com.keepeat.backend.domain.notification.service.NotificationService;
import com.keepeat.backend.domain.useringredient.UserIngredientRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IngredientExpirySchedulerTest {

    @InjectMocks
    private IngredientExpiryScheduler scheduler;

    @Mock
    private UserIngredientRepository userIngredientRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private NotificationBatchRunRepository notificationBatchRunRepository;

    @Mock
    private TaskScheduler taskScheduler;

    private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

    @Test
    @DisplayName("배치는 시작과 완료를 기록한다")
    void batchRecordsStartAndCompletion() {
        given(userIngredientRepository.findExpiringUserIds(any(), any(), any())).willReturn(Page.empty());

        scheduler.checkExpiringIngredients();

        var order = inOrder(notificationBatchRunRepository, userIngredientRepository);
        order.verify(notificationBatchRunRepository).markStarted(eq(today), any(Instant.class));
        order.verify(userIngredientRepository).findExpiringUserIds(any(), any(), any());
        order.verify(notificationBatchRunRepository).markCompleted(eq(today), any(Instant.class));
    }

    @Test
    @DisplayName("저장에 실패한 유저가 있으면 완료로 기록하지 않아 재기동 때 다시 실행된다")
    void batchWithFailedUsersStaysIncomplete() {
        given(userIngredientRepository.findExpiringUserIds(any(), any(), any()))
                .willReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(1L)));
        com.keepeat.backend.domain.useringredient.UserIngredient item =
                org.mockito.Mockito.mock(com.keepeat.backend.domain.useringredient.UserIngredient.class);
        given(item.getUserId()).willReturn(1L);
        given(item.getExpiryDate()).willReturn(today.plusDays(1));
        given(item.getCustomName()).willReturn("우유");
        given(userIngredientRepository.findExpiringByUserIds(any(), any(), any())).willReturn(java.util.List.of(item));
        given(notificationService.sendNotificationOnce(any(), any(), any(), any(), any(), any()))
                .willThrow(new RuntimeException("db down"));

        scheduler.checkExpiringIngredients();

        verify(notificationBatchRunRepository).markStarted(eq(today), any(Instant.class));
        verify(notificationBatchRunRepository, never()).markCompleted(any(), any());
    }

    @Test
    @DisplayName("오늘 시작했지만 끝나지 않은 배치가 있으면 재기동 때 다시 실행한다")
    void interruptedRunIsResumed() {
        given(notificationBatchRunRepository.existsByRunDateAndCompletedAtIsNull(today)).willReturn(true);

        scheduler.resumeInterruptedRun();

        verify(taskScheduler).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    @DisplayName("끝난 배치나 시작하지 않은 배치는 재기동 때 실행하지 않는다")
    void completedOrMissingRunIsNotResumed() {
        given(notificationBatchRunRepository.existsByRunDateAndCompletedAtIsNull(today)).willReturn(false);

        scheduler.resumeInterruptedRun();

        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }
}
