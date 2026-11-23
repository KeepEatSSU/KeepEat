package com.keepeat.backend.domain.notification.service;

import com.keepeat.backend.domain.notification.entity.NotificationType;
import com.keepeat.backend.domain.notification.entity.PushDelivery;
import com.keepeat.backend.domain.notification.repository.DeviceTokenRepository;
import com.keepeat.backend.domain.notification.repository.PushDeliveryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class PushDispatchService {

    static final int WORKERS = 4;

    private final DeviceTokenRepository deviceTokenRepository;
    private final PushDeliveryRepository pushDeliveryRepository;
    private final ExpoPushService expoPushService;
    private final Executor pushExecutor;
    private final AtomicInteger running = new AtomicInteger();

    public PushDispatchService(DeviceTokenRepository deviceTokenRepository, PushDeliveryRepository pushDeliveryRepository,
                               ExpoPushService expoPushService, @Qualifier("pushExecutor") Executor pushExecutor) {
        this.deviceTokenRepository = deviceTokenRepository;
        this.pushDeliveryRepository = pushDeliveryRepository;
        this.expoPushService = expoPushService;
        this.pushExecutor = pushExecutor;
    }

    public void enqueue(Long userId, String title, String body, NotificationType type, Long notificationId) {
        Instant now = Instant.now();
        pushDeliveryRepository.saveAll(deviceTokenRepository.findAllByUserId(userId).stream()
                .map(token -> PushDelivery.pending(userId, notificationId, token.getToken(), title, body, type, now))
                .toList());
    }

    @Scheduled(fixedDelayString = "${app.push.send-poll-delay-ms:5000}",
            initialDelayString = "${app.push.send-poll-delay-ms:5000}")
    public void wake() {
        int current;
        while ((current = running.get()) < WORKERS) {
            if (running.compareAndSet(current, current + 1)) {
                pushExecutor.execute(this::drain);
            }
        }
    }

    private void drain() {
        try {
            boolean sent;
            do {
                sent = expoPushService.sendDue();
            } while (sent);
        } catch (Exception e) {
            log.warn("푸시 발송 작업 중단: cause={}", e.getClass().getSimpleName());
        } finally {
            running.decrementAndGet();
        }
    }
}
