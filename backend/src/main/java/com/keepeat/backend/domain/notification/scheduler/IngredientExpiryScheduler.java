package com.keepeat.backend.domain.notification.scheduler;

import com.keepeat.backend.domain.notification.entity.NotificationType;
import com.keepeat.backend.domain.notification.service.NotificationService;
import com.keepeat.backend.domain.useringredient.UserIngredient;
import com.keepeat.backend.domain.useringredient.UserIngredientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class IngredientExpiryScheduler {

    private final UserIngredientRepository userIngredientRepository; // 유저 식재료 레포지토리
    private final NotificationService notificationService; // 우리가 만든 알림 매니저

    // 초 분 시 일 월 요일 -> 매일 아침 9시 0분 0초에 실행 (Cron 표현식)
    @Scheduled(cron = "0 0 9 * * *")
    public void checkExpiringIngredients() {
        log.info("⏰ [소비기한 임박 알림 스케줄러] 작동 시작!");

        // 오늘 기준으로 딱 3일 남은 날짜 계산
        LocalDate targetDate = LocalDate.now().plusDays(3);

        // 소비기한이 딱 3일 남은 모든 식재료를 DB에서 찾기
        List<UserIngredient> expiringList = userIngredientRepository.findAllByExpiryDateAndUserNotDeleted(targetDate);

        // 유저별로 임박 식재료를 묶어서 통합 알림 1건씩만 보낸다 (푸시 폭탄 방지)
        Map<Long, List<UserIngredient>> groupedByUser = expiringList.stream()
                .collect(Collectors.groupingBy(UserIngredient::getUserId));

        for (Map.Entry<Long, List<UserIngredient>> entry : groupedByUser.entrySet()) {
            Long userId = entry.getKey();
            List<UserIngredient> userItems = entry.getValue();

            String title = buildTitle(userItems.size());
            String body = buildBody(userItems);
            // 식재료 1개일 때는 해당 상세로, 여러 개일 때는 임박 목록 화면으로 라우팅하도록 targetId를 비움
            String targetId = userItems.size() == 1
                    ? String.valueOf(userItems.get(0).getId())
                    : null;

            notificationService.sendNotification(
                    userId,
                    title,
                    body,
                    NotificationType.EXPIRY_SOON,
                    targetId
            );
        }

        log.info("⏰ [소비기한 임박 알림 스케줄러] 총 {}명의 유저에게 통합 알림 발송 완료 (임박 식재료 {}건)",
                groupedByUser.size(), expiringList.size());
    }

    // 식재료 이름: customName 우선, 없으면 기본 문구
    private String resolveItemName(UserIngredient item) {
        return item.getCustomName() != null ? item.getCustomName() : "등록된 식재료";
    }

    private String buildTitle(int itemCount) {
        if (itemCount == 1) {
            return "⏳ 식재료 소비기한 임박!";
        }
        return String.format("⏳ 식재료 %d개 소비기한 임박!", itemCount);
    }

    private String buildBody(List<UserIngredient> items) {
        // 1개: 기존 메시지 그대로 유지 (회귀 방지)
        if (items.size() == 1) {
            String itemName = resolveItemName(items.get(0));
            return String.format("냉장고 속 [%s]의 소비기한이 3일 남았습니다. 구출해 주세요!", itemName);
        }

        // 2개 이상: 앞 2개만 노출하고 나머지는 "외 N개"로 축약
        String firstTwo = items.stream()
                .limit(2)
                .map(item -> "[" + resolveItemName(item) + "]")
                .collect(Collectors.joining(", "));

        if (items.size() == 2) {
            return String.format("냉장고 속 %s의 소비기한이 3일 남았습니다. 빨리 구출해 주세요!", firstTwo);
        }

        int remaining = items.size() - 2;
        return String.format("냉장고 속 %s 외 %d개의 소비기한이 3일 남았습니다. 빨리 구출해 주세요!",
                firstTwo, remaining);
    }
}