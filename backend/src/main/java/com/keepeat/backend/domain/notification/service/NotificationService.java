package com.keepeat.backend.domain.notification.service;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final DeviceTokenRepository deviceTokenRepository;
    private final NotificationRepository notificationRepository;
    private final ExpoPushService expoPushService;
    private final AppUserRepository appUserRepository;

    @Transactional
    public void registerToken(Long userId, String token) {
        deviceTokenRepository.findByToken(token).orElseGet(() -> {
            DeviceToken newToken = new DeviceToken(userId, token);
            return deviceTokenRepository.save(newToken);
        });
        log.info("유저 {}의 새로운 기기 토큰이 등록되었습니다.", userId);
    }

    @Transactional
    public void sendNotification(Long userId, String title, String body, NotificationType type, String targetId) {

        // 1. 유저 정보 조회 (소문자 appUserRepository 사용!)
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));

        // 2. 기획 반영: 유저 알림 설정과 무관하게 히스토리(종 모양 버튼용)는 무조건 DB에 저장합니다.
        Notification notification = new Notification(userId, title, body, type, targetId);
        notificationRepository.save(notification);

        // 3. 기획 반영: 유저가 알림을 껐다면 여기서 메서드를 종료합니다. (푸시 안 쏨)
        if (!user.isNotificationEnabled()) {
            log.info("유저 {}는 알림을 끈 상태입니다. 히스토리만 저장하고 푸시 알림은 생략합니다.", userId);
            return;
        }

        // 4. 알림이 켜져 있는 유저라면 기기 토큰들을 꺼내서 엑스포로 푸시 발송!
        List<DeviceToken> tokens = deviceTokenRepository.findAllByUserId(userId);
        for (DeviceToken deviceToken : tokens) {
            PushSendRequest request = new PushSendRequest(deviceToken.getToken(), title, body, type, targetId);
            expoPushService.sendMessage(request);
        }
    }

    @Transactional(readOnly = true)
    public NotificationPageResponse getNotifications(Long userId, Long cursor, int size) {

        // 프론트에서 요구한 size보다 1개 더 많이 조회
        Pageable pageable = PageRequest.of(0, size + 1);

        // Repository에서 커서 기반으로 데이터 조회 (아까 만든 쿼리 메서드 호출)
        List<Notification> notifications = notificationRepository.findNotificationsByCursor(userId, cursor, pageable);

        // 다음 페이지 존재 여부 확인
        boolean hasNext = false;
        if (notifications.size() > size) {
            hasNext = true;
            notifications.remove(size); // 프론트에게는 원래 요청한 개수(size)만큼만 잘라서 줘야 하므로 마지막 1개는 뺌
        }

        // 엔티티를 DTO로 변환
        List<NotificationResponse> notificationResponses = notifications.stream()
                // .map(n -> new NotificationResponse(n.getId(), n.getTitle(), ...)) // DTO 생성 로직에 맞게 수정하세요!
                .map(NotificationResponse::from)
                .toList();

        // 다음 커서 값 계산 (마지막 알림의 ID)
        Long nextCursor = null;
        if (hasNext && !notificationResponses.isEmpty()) {
            nextCursor = notificationResponses.get(notificationResponses.size() - 1).id(); // DTO의 id 필드
        }

        return new NotificationPageResponse(notificationResponses, hasNext, nextCursor);
    }

    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 알림입니다."));

        if (!notification.getUserId().equals(userId)) {
            throw new IllegalStateException("해당 알림에 대한 권한이 없습니다.");
        }

        notification.markAsRead();
    }

    @Transactional
    public void deleteToken(Long userId, String token) {
        deviceTokenRepository.deleteByUserIdAndToken(userId, token);
        log.info("유저 {}의 기기 토큰이 삭제되었습니다 (로그아웃 처리).", userId);
    }

    @Transactional(readOnly = true)
    public int getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        // 벌크 연산 쿼리 실행 (업데이트된 알림 개수를 반환)
        int updatedCount = notificationRepository.markAllAsReadByUserId(userId);
        log.info("유저 {}의 알림 {}건이 모두 읽음 처리되었습니다.", userId, updatedCount);
    }
}