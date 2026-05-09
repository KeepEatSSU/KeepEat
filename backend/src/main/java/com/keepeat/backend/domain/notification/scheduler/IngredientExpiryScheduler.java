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
        List<UserIngredient> expiringList = userIngredientRepository.findAllByExpiryDate(targetDate);

        // 찾은 식재료들의 주인(User)에게 각각 알림 쏘기
        for (UserIngredient item : expiringList) {
            String title = "⏳ 식재료 소비기한 임박!";
            // 식재료에 커스텀 이름이 있으면 그걸 쓰고, 없으면 원래 이름을 쓰도록 처리
            String itemName = item.getCustomName() != null ? item.getCustomName() : "등록된 식재료";
            String body = String.format("냉장고 속 [%s]의 소비기한이 3일 남았습니다. 구출해 주세요!", itemName);

            // 알림 전송 (Type은 INGREDIENT_EXPIRY, targetId는 해당 식재료의 ID)
            notificationService.sendNotification(
                    item.getUserId(),
                    title,
                    body,
                    NotificationType.EXPIRY_SOON,
                    String.valueOf(item.getId())
            );
        }

        log.info("⏰ [소비기한 임박 알림 스케줄러] 총 {}건의 알림 발송 완료!", expiringList.size());
    }
}