package com.keepeat.backend.domain.notification.scheduler;

import com.keepeat.backend.domain.notification.entity.NotificationType;
import com.keepeat.backend.domain.notification.service.NotificationService;
import com.keepeat.backend.domain.user.entity.AppUser;
import com.keepeat.backend.domain.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeeklyCheerScheduler {

    private final AppUserRepository appUserRepository;
    private final NotificationService notificationService;

    // 매주 월요일 낮 12시에 실행 (초 분 시 일 월 요일)
    @Scheduled(cron = "0 0 12 * * MON")
    public void sendWeeklyCheer() {
        log.info("⏰ [주간 응원 알림 스케줄러] 작동 시작!");

        // 활성 유저 조회 — AppUser의 @SQLRestriction("deleted_at IS NULL")로 인해
        // findAll() 결과에서 소프트 삭제된 유저는 자동으로 제외된다.
        List<AppUser> activeUsers = appUserRepository.findAll();

        // 루프 돌면서 알림 쏘기
        for (AppUser user : activeUsers) {
            String title = "💪 이번 주도 파이팅!";
            String body = "냉장고 속 식재료 구출, KeepEat과 함께 시작해 볼까요?";

            notificationService.sendNotification(
                    user.getId(),
                    title,
                    body,
                    NotificationType.WEEKLY_CHEER,
                    null
            );
        }

        log.info("⏰ [주간 응원 알림 스케줄러] 활성 유저 {}명에게 알림 발송 완료!", activeUsers.size());
    }
}