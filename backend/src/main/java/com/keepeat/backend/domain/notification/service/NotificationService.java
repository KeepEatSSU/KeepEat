package com.keepeat.backend.domain.notification.service;

import com.keepeat.backend.domain.common.exception.ErrorCode;
import com.keepeat.backend.domain.common.exception.KeepEatException;
import com.keepeat.backend.domain.notification.dto.NotificationPageResponse;
import com.keepeat.backend.domain.notification.dto.NotificationResponse;
import com.keepeat.backend.domain.notification.dto.PushSendRequest;
import com.keepeat.backend.domain.notification.entity.DeviceToken;
import com.keepeat.backend.domain.notification.entity.Notification;
import com.keepeat.backend.domain.notification.entity.NotificationType;
import com.keepeat.backend.domain.notification.repository.DeviceTokenRepository;
import com.keepeat.backend.domain.notification.repository.NotificationRepository;
import com.keepeat.backend.domain.user.entity.AppUser;
import com.keepeat.backend.domain.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 100;

    private final DeviceTokenRepository deviceTokenRepository;
    private final NotificationRepository notificationRepository;
    private final ExpoPushService expoPushService;
    private final AppUserRepository appUserRepository;

    @Transactional
    public void registerToken(Long userId, String token) {
        requireActiveUser(userId);

        deviceTokenRepository.findByToken(token).ifPresentOrElse(existingToken -> {
            if (!existingToken.getUserId().equals(userId)) {
                existingToken.reassignTo(userId);
                log.info("기기 토큰 소유자를 유저 {}로 변경했습니다.", userId);
            }
        }, () -> {
            DeviceToken newToken = new DeviceToken(userId, token);
            deviceTokenRepository.save(newToken);
            log.info("유저 {}의 새로운 기기 토큰이 등록되었습니다.", userId);
        });
    }

    @Transactional
    public void sendNotification(Long userId, String title, String body, NotificationType type, String targetId) {
        sendNotificationInternal(userId, title, body, type, targetId, null);
    }

    @Transactional
    public boolean sendNotificationOnce(Long userId, String title, String body, NotificationType type, String targetId, String dedupeKey) {
        if (notificationRepository.existsByDedupeKey(dedupeKey)) {
            log.info("중복 알림을 생략했습니다. dedupeKey={}", dedupeKey);
            return false;
        }

        sendNotificationInternal(userId, title, body, type, targetId, dedupeKey);
        return true;
    }

    @Transactional(readOnly = true)
    public NotificationPageResponse getNotifications(Long userId, Long cursor, int size) {
        requireActiveUser(userId);
        validateCursor(cursor);
        validatePageSize(size);

        Pageable pageable = PageRequest.of(0, size + 1);
        List<Notification> notifications = notificationRepository.findNotificationsByCursor(userId, cursor, pageable);

        boolean hasNext = false;
        if (notifications.size() > size) {
            hasNext = true;
            notifications.remove(size);
        }

        List<NotificationResponse> notificationResponses = notifications.stream()
                .map(NotificationResponse::from)
                .toList();

        Long nextCursor = null;
        if (hasNext && !notificationResponses.isEmpty()) {
            nextCursor = notificationResponses.get(notificationResponses.size() - 1).id();
        }

        return new NotificationPageResponse(notificationResponses, hasNext, nextCursor);
    }

    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        requireActiveUser(userId);

        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new KeepEatException(ErrorCode.NOTIFICATION_NOT_FOUND));

        if (!notification.getUserId().equals(userId)) {
            throw new KeepEatException(ErrorCode.NOTIFICATION_ACCESS_DENIED);
        }

        notification.markAsRead();
    }

    @Transactional
    public void deleteToken(Long userId, String token) {
        requireActiveUser(userId);
        deviceTokenRepository.deleteByUserIdAndToken(userId, token);
        log.info("유저 {}의 기기 토큰이 삭제되었습니다 (로그아웃 처리).", userId);
    }

    @Transactional(readOnly = true)
    public int getUnreadCount(Long userId) {
        requireActiveUser(userId);
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        requireActiveUser(userId);
        int updatedCount = notificationRepository.markAllAsReadByUserId(userId);
        log.info("유저 {}의 알림 {}건이 모두 읽음 처리되었습니다.", userId, updatedCount);
    }

    @Transactional
    public void markAllAsReadByType(Long userId, NotificationType type) {
        requireActiveUser(userId);
        int updatedCount = notificationRepository.markAllAsReadByUserIdAndType(userId, type);
        log.info("유저 {}의 {} 타입 알림 {}건이 모두 읽음 처리되었습니다.", userId, type, updatedCount);
    }

    @Transactional
    public void deleteNotification(Long notificationId, Long userId) {
        requireActiveUser(userId);
        int deleted = notificationRepository.deleteByIdAndUserId(notificationId, userId);
        if (deleted == 0) {
            throw new KeepEatException(ErrorCode.NOTIFICATION_NOT_FOUND);
        }
        log.info("유저 {}가 알림 {} 1건을 삭제했습니다.", userId, notificationId);
    }

    @Transactional
    public void deleteNotifications(List<Long> ids, Long userId) {
        requireActiveUser(userId);
        int deleted = notificationRepository.deleteAllByIdInAndUserId(ids, userId);
        log.info("유저 {}가 알림 {}건을 일괄 삭제했습니다. (요청 {}건 중)", userId, deleted, ids.size());
    }

    @Transactional
    public void sendTestNotification(Long userId, NotificationType type, String title, String message) {
        String resolvedTitle = (title != null && !title.isBlank()) ? title : defaultTitle(type);
        String resolvedBody = (message != null && !message.isBlank()) ? message : defaultBody(type);

        sendNotification(userId, resolvedTitle, resolvedBody, type, null);
        log.info("유저 {} 에게 테스트 알림 발사 (type={})", userId, type);
    }

    private void sendNotificationInternal(Long userId, String title, String body, NotificationType type, String targetId, String dedupeKey) {
        AppUser user = requireActiveUser(userId);

        Notification notification = new Notification(userId, title, body, type, targetId, dedupeKey);
        notificationRepository.saveAndFlush(notification);

        if (!user.isNotificationEnabled()) {
            log.info("유저 {}는 알림을 끈 상태입니다. 히스토리만 저장하고 푸시 알림은 생략합니다.", userId);
            return;
        }

        sendPushAfterCommit(userId, title, body, type, targetId);
    }

    private void sendPushAfterCommit(Long userId, String title, String body, NotificationType type, String targetId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            sendPush(userId, title, body, type, targetId);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sendPush(userId, title, body, type, targetId);
            }
        });
    }

    private void sendPush(Long userId, String title, String body, NotificationType type, String targetId) {
        List<DeviceToken> tokens = deviceTokenRepository.findAllByUserId(userId);
        for (DeviceToken deviceToken : tokens) {
            PushSendRequest request = new PushSendRequest(deviceToken.getToken(), title, body, type, targetId);
            expoPushService.sendMessage(request);
        }
    }

    private AppUser requireActiveUser(Long userId) {
        return appUserRepository.findById(userId)
                .orElseThrow(() -> new KeepEatException(ErrorCode.USER_NOT_FOUND));
    }

    private void validatePageSize(int size) {
        if (size < MIN_PAGE_SIZE || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("알림 조회 size는 1 이상 100 이하만 허용됩니다.");
        }
    }

    private void validateCursor(Long cursor) {
        if (cursor != null && cursor <= 0) {
            throw new IllegalArgumentException("잘못된 커서 형식입니다.");
        }
    }

    private String defaultTitle(NotificationType type) {
        return switch (type) {
            case NOTICE -> "📢 [TEST] 공지사항";
            case EXPIRY_SOON -> "⏳ [TEST] 식재료 소비기한 임박";
            case RECIPE_READY -> "🍳 [TEST] AI 레시피 생성 완료";
        };
    }

    private String defaultBody(NotificationType type) {
        return switch (type) {
            case NOTICE -> "테스트 공지사항입니다.";
            case EXPIRY_SOON -> "테스트 알림입니다. 냉장고 속 식재료의 소비기한이 임박했습니다.";
            case RECIPE_READY -> "테스트 알림입니다. AI 레시피가 준비되었습니다.";
        };
    }
}
