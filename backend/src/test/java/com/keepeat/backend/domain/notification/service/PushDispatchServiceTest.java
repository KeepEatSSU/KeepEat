package com.keepeat.backend.domain.notification.service;

import com.keepeat.backend.domain.notification.entity.DeviceToken;
import com.keepeat.backend.domain.notification.entity.NotificationType;
import com.keepeat.backend.domain.notification.entity.PushDelivery;
import com.keepeat.backend.domain.notification.entity.PushDeliveryStatus;
import com.keepeat.backend.domain.notification.repository.DeviceTokenRepository;
import com.keepeat.backend.domain.notification.repository.PushDeliveryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class PushDispatchServiceTest {

    private final DeviceTokenRepository deviceTokenRepository = mock(DeviceTokenRepository.class);
    private final PushDeliveryRepository pushDeliveryRepository = mock(PushDeliveryRepository.class);
    private final ExpoPushService expoPushService = mock(ExpoPushService.class);
    private final List<Runnable> submitted = new ArrayList<>();
    private final Executor executor = submitted::add;
    private final PushDispatchService pushDispatchService =
            new PushDispatchService(deviceTokenRepository, pushDeliveryRepository, expoPushService, executor);

    @Test
    @DisplayName("enqueue는 기기 토큰마다 발송 대기 기록을 만든다")
    @SuppressWarnings("unchecked")
    void enqueue_storesPendingDeliveryPerToken() {
        DeviceToken phone = mock(DeviceToken.class);
        DeviceToken tablet = mock(DeviceToken.class);
        given(phone.getToken()).willReturn("ExpoPushToken[phone]");
        given(tablet.getToken()).willReturn("ExpoPushToken[tablet]");
        given(deviceTokenRepository.findAllByUserId(1L)).willReturn(List.of(phone, tablet));

        pushDispatchService.enqueue(1L, "title", "body", NotificationType.RECIPE_READY, 42L);

        ArgumentCaptor<List<PushDelivery>> captor = ArgumentCaptor.forClass(List.class);
        verify(pushDeliveryRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(PushDelivery::getToken)
                .containsExactly("ExpoPushToken[phone]", "ExpoPushToken[tablet]");
        assertThat(captor.getValue()).allSatisfy(delivery -> {
            assertThat(delivery.getStatus()).isEqualTo(PushDeliveryStatus.PENDING);
            assertThat(delivery.getNotificationId()).isEqualTo(42L);
            assertThat(delivery.getNextSendAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("wake를 여러 번 불러도 발송 작업은 정해진 수만 돈다")
    void wake_startsAtMostWorkers() {
        pushDispatchService.wake();
        pushDispatchService.wake();

        assertThat(submitted).hasSize(PushDispatchService.WORKERS);
    }

    @Test
    @DisplayName("발송 작업은 보낼 것이 없을 때까지 돌고, 실패해도 끝나면 다시 깨울 수 있다")
    void drain_runsUntilEmptyAndReleasesSlot() {
        given(expoPushService.sendDue()).willReturn(true, true, false).willThrow(new RuntimeException("db down"));

        pushDispatchService.wake();
        submitted.get(0).run();
        submitted.get(1).run();
        pushDispatchService.wake();

        verify(expoPushService, times(4)).sendDue();
        assertThat(submitted).hasSize(PushDispatchService.WORKERS + 2);
    }
}
