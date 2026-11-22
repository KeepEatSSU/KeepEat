package com.keepeat.backend.domain.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keepeat.backend.domain.notification.dto.PushSendRequest;
import com.keepeat.backend.domain.notification.entity.PushDelivery;
import com.keepeat.backend.domain.notification.entity.PushDeliveryStatus;
import com.keepeat.backend.domain.notification.repository.DeviceTokenRepository;
import com.keepeat.backend.domain.notification.repository.PushDeliveryRepository;
import com.keepeat.backend.domain.user.repository.AppUserRepository;
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
import org.springframework.web.client.RestClientException;
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

    private static final int MAX_SEND_ATTEMPTS = 3;
    private static final int RECEIPT_BATCH_SIZE = 1000;
    private static final Duration RECEIPT_RETENTION = Duration.ofHours(24);

    private final DeviceTokenRepository deviceTokenRepository;
    private final PushDeliveryRepository pushDeliveryRepository;
    private final AppUserRepository appUserRepository;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ExpoPushService(DeviceTokenRepository deviceTokenRepository, PushDeliveryRepository pushDeliveryRepository,
                           AppUserRepository appUserRepository, PlatformTransactionManager transactionManager) {
        this.deviceTokenRepository = deviceTokenRepository;
        this.pushDeliveryRepository = pushDeliveryRepository;
        this.appUserRepository = appUserRepository;
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

    public void sendMessage(PushSendRequest request) {
        for (int attempt = 1; attempt <= MAX_SEND_ATTEMPTS; attempt++) {
            try {
                JsonNode response = post(expoPushUrl, buildPushBody(request));
                handleTicketResponse(response.path("data"), request);
                return;
            } catch (RestClientException | RetryablePushException e) {
                if (attempt == MAX_SEND_ATTEMPTS) {
                    log.warn("Expo 푸시 요청 실패: token={}, attempts={}", fingerprint(request.targetToken()), attempt);
                    return;
                }
                backoff(attempt);
            } catch (Exception e) {
                log.warn("Expo 푸시 응답 처리 실패: token={}, cause={}",
                        fingerprint(request.targetToken()), e.getClass().getSimpleName());
                return;
            }
        }
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
                int finished = pushDeliveryRepository.finish(List.of(delivery.getId()), PushDeliveryStatus.FAILED, error);
                if (finished == 1 && "MessageRateExceeded".equals(error)) {
                    sendMessage(new PushSendRequest(delivery.getUserId(), delivery.getToken(), delivery.getTitle(),
                            delivery.getBody(), delivery.getNotificationType(), delivery.getNotificationId()));
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

    private void handleTicketResponse(JsonNode ticket, PushSendRequest request) {
        if (ticket.isArray() && !ticket.isEmpty()) {
            ticket = ticket.get(0);
        }
        String status = ticket.path("status").asText();
        if ("ok".equals(status)) {
            String ticketId = ticket.path("id").asText(null);
            if (ticketId != null) {
                Instant now = Instant.now();
                transactionTemplate.executeWithoutResult(tx -> appUserRepository.findByIdForShare(request.userId())
                        .ifPresent(user -> pushDeliveryRepository.save(PushDelivery.ticketed(request.userId(),
                                request.notificationId(), request.targetToken(), request.title(), request.body(),
                                request.type(), ticketId, now, now.plusMillis(receiptFirstCheckDelayMs)))));
            }
            return;
        }
        if ("error".equals(status)) {
            String error = handleDeliveryError(request.targetToken(), ticket);
            if ("MessageRateExceeded".equals(error)) {
                throw new RetryablePushException();
            }
            return;
        }
        log.warn("Expo 푸시 응답 형식이 올바르지 않음: token={}", fingerprint(request.targetToken()));
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

    Map<String, Object> buildPushBody(PushSendRequest request) {
        Map<String, Object> data = new HashMap<>();
        data.put("type", request.type().name());
        if (request.notificationId() != null) {
            data.put("notificationId", request.notificationId());
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("to", request.targetToken());
        body.put("title", request.title());
        body.put("body", request.body());
        body.put("data", data);
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

    private void backoff(int attempt) {
        try {
            Thread.sleep(200L * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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

    private static final class RetryablePushException extends RuntimeException {
    }
}
