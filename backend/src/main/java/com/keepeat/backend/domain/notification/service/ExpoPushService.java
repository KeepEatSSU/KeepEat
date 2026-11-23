package com.keepeat.backend.domain.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepeat.backend.domain.notification.entity.PushDelivery;
import com.keepeat.backend.domain.notification.entity.PushDeliveryStatus;
import com.keepeat.backend.domain.notification.repository.DeviceTokenRepository;
import com.keepeat.backend.domain.notification.repository.PushDeliveryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ExpoPushService {

    private static final int SEND_BATCH_SIZE = 10;
    private static final Duration SEND_LEASE = Duration.ofMinutes(2);
    private static final Duration SEND_RETRY_BASE = Duration.ofSeconds(5);
    private static final Duration SEND_RETRY_MAX = Duration.ofMinutes(10);
    private static final Duration SEND_RETENTION = Duration.ofHours(24);
    private static final int RECEIPT_BATCH_SIZE = 1000;
    private static final Duration RECEIPT_RETENTION = Duration.ofHours(24);

    private final DeviceTokenRepository deviceTokenRepository;
    private final PushDeliveryRepository pushDeliveryRepository;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ExpoPushService(DeviceTokenRepository deviceTokenRepository, PushDeliveryRepository pushDeliveryRepository,
                           PlatformTransactionManager transactionManager) {
        this.deviceTokenRepository = deviceTokenRepository;
        this.pushDeliveryRepository = pushDeliveryRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    private final RestTemplate restTemplate = createRestTemplate();

    // 테스트 환경에서 Expo mock으로 바꿀 수 있도록 설정값으로 둔다 (#51). 기본값은 실제 Expo 주소다.
    @Value("${app.push.expo-push-url:https://exp.host/--/api/v2/push/send}")
    private String expoPushUrl = "https://exp.host/--/api/v2/push/send";

    @Value("${app.push.expo-receipts-url:https://exp.host/--/api/v2/push/getReceipts}")
    private String expoReceiptsUrl = "https://exp.host/--/api/v2/push/getReceipts";

    @Value("${app.push.receipt-first-check-delay-ms:900000}")
    private long receiptFirstCheckDelayMs = 900_000;

    @Value("${app.push.receipt-retry-delay-ms:900000}")
    private long receiptRetryDelayMs = 900_000;

    public boolean sendDue() {
        Instant now = Instant.now();
        Instant leaseUntil = now.plus(SEND_LEASE);
        List<PushDelivery> rows = transactionTemplate.execute(tx -> {
            List<PushDelivery> due = pushDeliveryRepository.lockDueForSend(now, SEND_BATCH_SIZE);
            due.forEach(row -> row.claimSend(leaseUntil));
            return due;
        });
        rows.forEach(this::send);
        return !rows.isEmpty();
    }

    void send(PushDelivery delivery) {
        JsonNode ticket;
        try {
            ticket = post(expoPushUrl, buildPushBody(delivery)).path("data");
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 429) {
                retryLater(delivery, "HTTP 429");
            } else {
                log.warn("Expo 푸시 요청 거부: token={}, status={}", fingerprint(delivery.getToken()), e.getStatusCode().value());
                pushDeliveryRepository.finish(List.of(delivery.getId()), PushDeliveryStatus.FAILED, "HTTP " + e.getStatusCode().value());
            }
            return;
        } catch (Exception e) {
            log.warn("Expo 푸시 요청 실패: token={}, attempts={}, cause={}",
                    fingerprint(delivery.getToken()), delivery.getSendAttempts(), e.getClass().getSimpleName());
            retryLater(delivery, e.getClass().getSimpleName());
            return;
        }

        if (ticket.isArray() && !ticket.isEmpty()) {
            ticket = ticket.get(0);
        }
        String status = ticket.path("status").asText();
        String ticketId = ticket.path("id").asText(null);
        if ("ok".equals(status) && ticketId != null) {
            Instant now = Instant.now();
            pushDeliveryRepository.markTicketed(delivery.getId(), ticketId, now, now.plusMillis(receiptFirstCheckDelayMs));
        } else if ("error".equals(status)) {
            String error = handleDeliveryError(delivery.getToken(), ticket);
            if ("MessageRateExceeded".equals(error)) {
                retryLater(delivery, error);
            } else {
                pushDeliveryRepository.finish(List.of(delivery.getId()), PushDeliveryStatus.FAILED, error);
            }
        } else {
            log.warn("Expo 푸시 응답 형식이 올바르지 않음: token={}", fingerprint(delivery.getToken()));
            retryLater(delivery, "InvalidResponse");
        }
    }

    private void retryLater(PushDelivery delivery, String error) {
        Instant now = Instant.now();
        if (!delivery.getCreatedAt().plus(SEND_RETENTION).isAfter(now)) {
            log.warn("Expo 푸시 재시도 기한 만료: token={}, error={}", fingerprint(delivery.getToken()), error);
            pushDeliveryRepository.finish(List.of(delivery.getId()), PushDeliveryStatus.FAILED, error);
            return;
        }
        int exponent = Math.min(Math.max(delivery.getSendAttempts() - 1, 0), 10);
        Duration delay = SEND_RETRY_BASE.multipliedBy(1L << exponent);
        pushDeliveryRepository.retrySend(delivery.getId(), now.plus(delay.compareTo(SEND_RETRY_MAX) > 0 ? SEND_RETRY_MAX : delay), error);
    }

    @Scheduled(fixedDelayString = "${app.push.receipt-check-delay-ms:60000}",
            initialDelayString = "${app.push.receipt-check-delay-ms:60000}")
    public void checkReceipts() {
        List<PushDelivery> due;
        do {
            due = claimDueReceipts(Instant.now());
            if (!due.isEmpty()) {
                checkReceipts(due);
            }
        } while (due.size() == RECEIPT_BATCH_SIZE);
    }

    private List<PushDelivery> claimDueReceipts(Instant now) {
        Instant retryAt = now.plusMillis(receiptRetryDelayMs);
        return transactionTemplate.execute(status -> {
            List<PushDelivery> rows = pushDeliveryRepository.lockDueForReceiptCheck(now, RECEIPT_BATCH_SIZE);
            rows.forEach(row -> row.claimReceiptCheck(retryAt));
            return rows;
        });
    }

    private void checkReceipts(List<PushDelivery> due) {
        JsonNode data;
        try {
            data = post(expoReceiptsUrl, Map.of("ids", due.stream().map(PushDelivery::getTicketId).toList())).path("data");
        } catch (Exception e) {
            log.warn("Expo receipt 조회 실패: count={}, cause={}", due.size(), e.getClass().getSimpleName());
            expire(due.stream().filter(delivery -> isPastRetention(delivery, Instant.now())).toList());
            return;
        }

        Instant now = Instant.now();
        List<Long> delivered = new ArrayList<>();
        List<PushDelivery> expired = new ArrayList<>();
        for (PushDelivery delivery : due) {
            JsonNode receipt = data.path(delivery.getTicketId());
            String status = receipt.path("status").asText();
            if ("ok".equals(status)) {
                delivered.add(delivery.getId());
            } else if ("error".equals(status)) {
                String error = handleDeliveryError(delivery.getToken(), receipt);
                if ("MessageRateExceeded".equals(error)) {
                    pushDeliveryRepository.retrySend(delivery.getId(), now.plus(SEND_RETRY_BASE), error);
                } else {
                    pushDeliveryRepository.finish(List.of(delivery.getId()), PushDeliveryStatus.FAILED, error);
                }
            } else if (isPastRetention(delivery, now)) {
                expired.add(delivery);
            }
        }
        if (!delivered.isEmpty()) {
            pushDeliveryRepository.finish(delivered, PushDeliveryStatus.DELIVERED, null);
        }
        expire(expired);
    }

    private boolean isPastRetention(PushDelivery delivery, Instant now) {
        return !delivery.getTicketedAt().plus(RECEIPT_RETENTION).isAfter(now);
    }

    private void expire(List<PushDelivery> deliveries) {
        if (deliveries.isEmpty()) {
            return;
        }
        deliveries.forEach(delivery -> log.warn("Expo receipt 확인 만료: token={}", fingerprint(delivery.getToken())));
        pushDeliveryRepository.finish(deliveries.stream().map(PushDelivery::getId).toList(), PushDeliveryStatus.EXPIRED, null);
    }

    private String handleDeliveryError(String token, JsonNode response) {
        String error = response.path("details").path("error").asText("unknown");
        if ("DeviceNotRegistered".equals(error)) {
            transactionTemplate.executeWithoutResult(tx -> deviceTokenRepository.deleteByToken(token));
            log.info("만료된 Expo 토큰 제거: token={}", fingerprint(token));
            return error;
        }
        log.warn("Expo 푸시 전달 실패: token={}, error={}", fingerprint(token), error);
        return error;
    }

    Map<String, Object> buildPushBody(PushDelivery delivery) {
        Map<String, Object> data = new HashMap<>();
        data.put("type", delivery.getNotificationType().name());
        if (delivery.getNotificationId() != null) {
            data.put("notificationId", delivery.getNotificationId());
        }

        String collapseKey = "push-" + delivery.getId();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("to", delivery.getToken());
        body.put("title", delivery.getTitle());
        body.put("body", delivery.getBody());
        body.put("data", data);
        body.put("collapseId", collapseKey);
        body.put("tag", collapseKey);
        return body;
    }

    private JsonNode post(String url, Map<String, ?> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        String response = restTemplate.postForObject(url, new HttpEntity<>(body, headers), String.class);
        try {
            return objectMapper.readTree(response == null ? "{}" : response);
        } catch (Exception e) {
            throw new IllegalStateException("Expo 응답 JSON 파싱에 실패했습니다.", e);
        }
    }

    private String fingerprint(String token) {
        if (token == null) {
            return "null";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest, 0, 6);
        } catch (Exception e) {
            return "unavailable";
        }
    }

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(5_000);
        return new RestTemplate(factory);
    }
}
