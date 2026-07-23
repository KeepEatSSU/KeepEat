package com.keepeat.backend.domain.notification.controller;

import com.keepeat.backend.domain.notification.dto.DeviceTokenRequest;
import com.keepeat.backend.domain.notification.dto.NotificationBulkDeleteRequest;
import com.keepeat.backend.domain.notification.dto.NotificationPageResponse;
import com.keepeat.backend.domain.notification.dto.NotificationResponse;
import com.keepeat.backend.domain.notification.dto.NotificationTestRequest;
import com.keepeat.backend.domain.notification.entity.NotificationType;
import com.keepeat.backend.domain.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Notification API", description = "푸시 알림 및 기기 토큰 관리 API")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "기기 푸시 토큰 등록", description = "클라이언트 기기의 푸시 토큰(Expo)을 서버에 등록합니다.")
    @PostMapping("/token")
    public ResponseEntity<String> registerToken(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody DeviceTokenRequest request) {

        notificationService.registerToken(userId, request.token());
        return ResponseEntity.ok("기기 토큰이 성공적으로 등록되었습니다.");
    }

    @Operation(summary = "알림 목록 조회 (무한 스크롤)", description = "커서 기반 페이징을 통해 유저의 알림 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<com.keepeat.backend.domain.notification.dto.NotificationPageResponse> getNotifications(
            @AuthenticationPrincipal Long userId,
            // 프론트에서 넘겨줄 커서와 사이즈를 받도록 파라미터 추가
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size
    ) {
        com.keepeat.backend.domain.notification.dto.NotificationPageResponse response =
                notificationService.getNotifications(userId, cursor, size);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "알림 읽음 처리", description = "특정 알림을 읽음 상태로 변경합니다.")
    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(
            @AuthenticationPrincipal Long userId,
            @PathVariable("id") Long notificationId) {

        notificationService.markAsRead(notificationId, userId);
        return ResponseEntity.noContent().build();
    }


    @Operation(summary = "기기 푸시 토큰 삭제", description = "로그아웃 시 해당 기기의 푸시 토큰을 삭제합니다.")
    @DeleteMapping("/token")
    public ResponseEntity<Void> deleteToken(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody DeviceTokenRequest request) {
        notificationService.deleteToken(userId, request.token());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "안 읽은 알림 개수 조회", description = "종 모양 아이콘 배지에 표시할 안 읽은 알림 개수를 반환합니다.")
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Integer>> getUnreadCount(@AuthenticationPrincipal Long userId) {

        int count = notificationService.getUnreadCount(userId);

        // 간단한 JSON 형태로 응답하기 위해 Map 객체 사용
        return ResponseEntity.ok(Map.of("unreadCount", count));
    }

    @Operation(summary = "모든 알림 읽음 처리", description = "사용자의 읽지 않은 모든 알림을 한 번에 읽음 상태로 변경합니다.")
    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(@AuthenticationPrincipal Long userId) {

        notificationService.markAllAsRead(userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "타입별 알림 일괄 읽음 처리",
            description = "지정한 타입(NOTICE / EXPIRY_SOON / RECIPE_READY)의 알림만 모두 읽음 상태로 변경합니다.")
    @PatchMapping("/read-all/{type}")
    public ResponseEntity<Void> markAllAsReadByType(
            @AuthenticationPrincipal Long userId,
            @PathVariable("type") NotificationType type) {

        notificationService.markAllAsReadByType(userId, type);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "알림 단일 삭제",
            description = "특정 알림 한 건을 삭제합니다. 본인 소유가 아니거나 존재하지 않으면 400.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNotification(
            @AuthenticationPrincipal Long userId,
            @PathVariable("id") Long notificationId) {

        notificationService.deleteNotification(notificationId, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "알림 일괄 삭제",
            description = "선택한 여러 알림을 한 번에 삭제합니다. 본인 소유 알림만 삭제되며, " +
                    "본인 소유가 아닌 ID가 섞여 있어도 에러 없이 조용히 무시됩니다.")
    @DeleteMapping
    public ResponseEntity<Void> deleteNotifications(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody NotificationBulkDeleteRequest request) {

        notificationService.deleteNotifications(request.ids(), userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "[TEST]",
            description = " 자기 자신에게 즉시 알림을 발사. " +
                    "type은 필수(NOTICE / EXPIRY_SOON / RECIPE_READY), title/message는 선택 (없으면 타입별 기본값). ")
    @PostMapping("/test")
    public ResponseEntity<Void> sendTestNotification(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody NotificationTestRequest request) {

        notificationService.sendTestNotification(
                userId,
                request.type(),
                request.title(),
                request.message()
        );
        return ResponseEntity.noContent().build();
    }
}