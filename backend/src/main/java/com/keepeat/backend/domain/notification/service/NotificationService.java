package com.keepeat.backend.domain.notification.service;

import com.keepeat.backend.domain.common.exception.ErrorCode;
import com.keepeat.backend.domain.common.exception.KeepEatException;
import com.keepeat.backend.domain.notification.dto.NotificationPageResponse;
import com.keepeat.backend.domain.notification.dto.NotificationResponse;
import com.keepeat.backend.domain.notification.dto.PushSendRequest;
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

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 100;

    private final DeviceTokenRepository deviceTokenRepository;
    private final NotificationRepository notificationRepository;
    private final PushDispatchService pushDispatchService;
    private final AppUserRepository appUserRepository;

    @Transactional
    public void registerToken(Long userId, String sessionId, String token) {
        requireActiveUser(userId);
        deviceTokenRepository.upsertToken(userId, sessionId, token, LocalDateTime.now());
        log.info("기기 토큰 등록 또는 세션 갱신 완료: userId={}", userId);
    }

    @Transactional
    public void sendNotification(Long userId, String title, String body, NotificationType type, String targetId) {
        sendNotificationInternal(userId, title, body, type, targetId, null);
    }

    @Transactional
    public boolean sendNotificationOnce(Long userId, String title, String body, NotificationType type, String targetId, String dedupeKey) {
        AppUser user = requireActiveUser(userId);
        validateNotificationPayload(title, body, type, targetId, dedupeKey);
        if (dedupeKey == null || dedupeKey.isBlank()) {
            throw new IllegalArgumentException("중복 방지 알림에는 dedupeKey가 필요합니다.");
        }

        int inserted = notificationRepository.insertIfAbsent(
                userId,
                title,
                body,
                type.name(),
                targetId,
                dedupeKey,
                Instant.now()
        );
        if (inserted == 0) {
            log.info("중복 알림을 생략했습니다. dedupeKey={}", dedupeKey);
            return false;
        }

        if (user.isNotificationEnabled()) {
            sendPushAfterCommit(userId, title, body, type, targetId);
        } else {
            log.info("유저 {}는 알림을 끈 상태입니다. 히스토리만 저장하고 푸시 알림은 생략합니다.", userId);
        }
        return true;
    }

    @Transactional(readOnly = true)
    public NotificationPageResponse getNotifications(Long userId, Long cursor, int size) {
        requireActiveUser(userId);
        validateCursor(cursor);
        validatePageSize(size);

        Pageable pageable = PageRequest.of(0, size + 1);
        List<Notification> notifications = notificationRepository.findNotificationsByCursor(userId, cursor, pageable);

        boolean hasNext = notifications.size() > size;
        List<Notification> pageItems = hasNext ? notifications.subList(0, size) : notifications;

        List<NotificationResponse> notificationResponses = pageItems.stream()
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

        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new KeepEatException(ErrorCode.NOTIFICATION_NOT_FOUND));

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

    private void sendNotificationInternal(Long userId, String title, String body, NotificationType type, String targetId, String dedupeKey) {
        AppUser user = requireActiveUser(userId);
        validateNotificationPayload(title, body, type, targetId, dedupeKey);

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
                pushDispatchService.dispatch(userId, title, body, type, targetId);
            }
        });
    }

    private void sendPush(Long userId, String title, String body, NotificationType type, String targetId) {
        pushDispatchService.dispatch(userId, title, body, type, targetId);
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

    private void validateNotificationPayload(
            String title,
            String body,
            NotificationType type,
            String targetId,
            String dedupeKey
    ) {
        if (title == null || title.isBlank() || title.length() > 200) {
            throw new IllegalArgumentException("알림 제목은 1자 이상 200자 이하여야 합니다.");
        }
        if (body == null || body.isBlank() || body.length() > 1000) {
            throw new IllegalArgumentException("알림 본문은 1자 이상 1000자 이하여야 합니다.");
        }
        if (type == null) {
            throw new IllegalArgumentException("알림 타입은 필수입니다.");
        }
        if (targetId != null && targetId.length() > 255) {
            throw new IllegalArgumentException("알림 대상 ID는 255자 이하여야 합니다.");
        }
        if (dedupeKey != null && dedupeKey.length() > 160) {
            throw new IllegalArgumentException("알림 중복 방지 키는 160자 이하여야 합니다.");
        }
    }

}
