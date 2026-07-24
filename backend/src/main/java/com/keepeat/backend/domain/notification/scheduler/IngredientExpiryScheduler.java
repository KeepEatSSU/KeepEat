package com.keepeat.backend.domain.notification.scheduler;

import com.keepeat.backend.domain.notification.entity.NotificationType;
import com.keepeat.backend.domain.notification.service.NotificationService;
import com.keepeat.backend.domain.useringredient.UserIngredient;
import com.keepeat.backend.domain.useringredient.UserIngredientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class IngredientExpiryScheduler {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int USER_BATCH_SIZE = 200;

    private final UserIngredientRepository userIngredientRepository;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    public void checkExpiringIngredients() {
        LocalDate today = LocalDate.now(SEOUL);
        LocalDate targetDate = today.plusDays(3);
        AtomicInteger sentUsers = new AtomicInteger();
        AtomicInteger skippedUsers = new AtomicInteger();
        AtomicInteger failedUsers = new AtomicInteger();
        AtomicInteger itemCount = new AtomicInteger();

        int pageNumber = 0;
        Page<Long> userPage;
        do {
            userPage = userIngredientRepository.findExpiringUserIds(
                    today, targetDate, PageRequest.of(pageNumber++, USER_BATCH_SIZE)
            );
            if (userPage.isEmpty()) {
                continue;
            }

            List<UserIngredient> items = userIngredientRepository.findExpiringByUserIds(
                    userPage.getContent(), today, targetDate
            );
            itemCount.addAndGet(items.size());
            Map<Long, List<UserIngredient>> grouped = items.stream()
                    .collect(Collectors.groupingBy(UserIngredient::getUserId));

            grouped.forEach((userId, userItems) -> sendForUser(
                    today, userId, userItems, sentUsers, skippedUsers, failedUsers
            ));
        } while (userPage.hasNext());

        log.info("소비기한 알림 배치 완료: sentUsers={}, skippedUsers={}, failedUsers={}, items={}",
                sentUsers.get(), skippedUsers.get(), failedUsers.get(), itemCount.get());
    }

    private void sendForUser(
            LocalDate today,
            Long userId,
            List<UserIngredient> items,
            AtomicInteger sent,
            AtomicInteger skipped,
            AtomicInteger failed
    ) {
        if (items.isEmpty()) {
            return;
        }

        long minDays = minDaysLeft(today, items);
        String targetId = items.size() == 1 ? String.valueOf(items.get(0).getId()) : null;
        String dedupeKey = "EXPIRY_SOON:%s:%d".formatted(today, userId);
        try {
            boolean created = notificationService.sendNotificationOnce(
                    userId,
                    buildTitle(items.size()),
                    buildBody(items, minDays),
                    NotificationType.EXPIRY_SOON,
                    targetId,
                    dedupeKey
            );
            if (created) {
                sent.incrementAndGet();
            } else {
                skipped.incrementAndGet();
            }
        } catch (Exception e) {
            failed.incrementAndGet();
            log.warn("소비기한 알림 처리 실패: userId={}, dedupeKey={}, cause={}",
                    userId, dedupeKey, e.getClass().getSimpleName());
        }
    }

    private String resolveItemName(UserIngredient item) {
        if (item.getCustomName() != null && !item.getCustomName().isBlank()) {
            return item.getCustomName();
        }
        if (item.getIngredient() != null) {
            return item.getIngredient().getName();
        }
        return "등록된 식재료";
    }

    private String buildTitle(int itemCount) {
        return itemCount == 1
                ? "⏳ 식재료 소비기한 임박!"
                : String.format("⏳ 식재료 %d개 소비기한 임박!", itemCount);
    }

    private String buildBody(List<UserIngredient> items, long minDays) {
        if (items.size() == 1) {
            String itemName = resolveItemName(items.get(0));
            return minDays == 0
                    ? String.format("냉장고 속 [%s]의 소비기한이 오늘까지입니다. 오늘 안에 드세요!", itemName)
                    : String.format("냉장고 속 [%s]의 소비기한이 %d일 남았습니다. 구출해 주세요!", itemName, minDays);
        }

        String firstTwo = items.stream()
                .limit(2)
                .map(item -> "[" + resolveItemName(item) + "]")
                .collect(Collectors.joining(", "));
        if (items.size() == 2) {
            return minDays == 0
                    ? String.format("냉장고 속 %s의 소비기한이 오늘까지입니다. 빨리 구출해 주세요!", firstTwo)
                    : String.format("냉장고 속 %s의 소비기한이 %d일 남았습니다. 빨리 구출해 주세요!", firstTwo, minDays);
        }

        int remaining = items.size() - 2;
        return minDays == 0
                ? String.format("냉장고 속 %s 외 %d개의 소비기한이 오늘까지입니다. 빨리 구출해 주세요!", firstTwo, remaining)
                : String.format("냉장고 속 %s 외 %d개의 소비기한이 %d일 남았습니다. 빨리 구출해 주세요!", firstTwo, remaining, minDays);
    }

    private long minDaysLeft(LocalDate today, List<UserIngredient> items) {
        return items.stream()
                .mapToLong(item -> ChronoUnit.DAYS.between(today, item.getExpiryDate()))
                .min()
                .orElse(0L);
    }
}
