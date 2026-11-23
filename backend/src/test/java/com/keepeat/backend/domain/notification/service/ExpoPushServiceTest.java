package com.keepeat.backend.domain.notification.service;

import com.keepeat.backend.domain.notification.entity.NotificationType;
import com.keepeat.backend.domain.notification.entity.PushDelivery;
import com.keepeat.backend.domain.notification.repository.DeviceTokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ExpoPushServiceTest {

    @InjectMocks
    private ExpoPushService expoPushService;

    @Mock
    private DeviceTokenRepository deviceTokenRepository;

    @Test
    @DisplayName("푸시 payload data에는 notificationId가 숫자로 포함되고 targetId는 포함되지 않는다")
    void buildPushBody_containsNumericNotificationId_withoutTargetId() {
        PushDelivery request = delivery(NotificationType.EXPIRY_SOON, 42L);

        Map<String, Object> body = expoPushService.buildPushBody(request);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        assertThat(data.get("notificationId")).isEqualTo(42L);
        assertThat(data.get("type")).isEqualTo("EXPIRY_SOON");
        assertThat(data).doesNotContainKey("targetId");
    }

    @Test
    @DisplayName("notificationId가 없으면 data에 notificationId 키를 넣지 않는다")
    void buildPushBody_withoutNotificationId_omitsKey() {
        PushDelivery request = delivery(NotificationType.NOTICE, null);

        Map<String, Object> body = expoPushService.buildPushBody(request);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        assertThat(data).doesNotContainKey("notificationId");
        assertThat(data.get("type")).isEqualTo("NOTICE");
    }

    @Test
    @DisplayName("같은 발송 기록은 재발송해도 같은 collapseId·tag를 써서 기기에 하나만 보인다")
    void buildPushBody_usesDeliveryIdAsCollapseKey() {
        PushDelivery request = delivery(NotificationType.EXPIRY_SOON, 42L);

        Map<String, Object> first = expoPushService.buildPushBody(request);
        Map<String, Object> second = expoPushService.buildPushBody(request);

        assertThat(first.get("collapseId")).isEqualTo("push-7");
        assertThat(first.get("tag")).isEqualTo("push-7");
        assertThat(second.get("collapseId")).isEqualTo(first.get("collapseId"));
    }

    private PushDelivery delivery(NotificationType type, Long notificationId) {
        PushDelivery delivery = PushDelivery.pending(1L, notificationId, "ExpoPushToken[test]", "title", "body", type, Instant.now());
        ReflectionTestUtils.setField(delivery, "id", 7L);
        return delivery;
    }
}
