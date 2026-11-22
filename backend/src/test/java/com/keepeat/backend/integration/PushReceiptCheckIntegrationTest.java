package com.keepeat.backend.integration;

import com.keepeat.backend.domain.notification.dto.PushSendRequest;
import com.keepeat.backend.domain.notification.entity.DeviceToken;
import com.keepeat.backend.domain.notification.entity.NotificationType;
import com.keepeat.backend.domain.notification.entity.PushDelivery;
import com.keepeat.backend.domain.notification.entity.PushDeliveryStatus;
import com.keepeat.backend.domain.notification.repository.DeviceTokenRepository;
import com.keepeat.backend.domain.notification.repository.PushDeliveryRepository;
import com.keepeat.backend.domain.notification.service.ExpoPushService;
import com.keepeat.backend.domain.user.entity.AppUser;
import com.keepeat.backend.domain.user.entity.Role;
import com.keepeat.backend.domain.user.repository.AppUserRepository;
import com.keepeat.backend.domain.user.service.AppUserService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class PushReceiptCheckIntegrationTest {

    private static final HttpServer EXPO = startExpoStub();
    private static final Map<String, String> RECEIPTS = new ConcurrentHashMap<>();
    private static final List<Integer> RECEIPT_REQUEST_SIZES = new CopyOnWriteArrayList<>();
    private static final Pattern ID = Pattern.compile("\"([^\"]+)\"");
    private static final AtomicBoolean RECEIPTS_DOWN = new AtomicBoolean();
    private static final String TICKET_OK = "{\"data\":{\"status\":\"ok\",\"id\":\"ticket-from-stub\"}}";
    private static final java.util.concurrent.atomic.AtomicReference<String> SEND_RESPONSE =
            new java.util.concurrent.atomic.AtomicReference<>(TICKET_OK);

    @Autowired
    private ExpoPushService expoPushService;

    @Autowired
    private PushDeliveryRepository pushDeliveryRepository;

    @Autowired
    private DeviceTokenRepository deviceTokenRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private AppUserService appUserService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long userId;

    @DynamicPropertySource
    static void expoUrls(DynamicPropertyRegistry registry) {
        String base = "http://127.0.0.1:" + EXPO.getAddress().getPort();
        registry.add("app.push.expo-push-url", () -> base + "/send");
        registry.add("app.push.expo-receipts-url", () -> base + "/receipts");
    }

    @AfterAll
    static void stopExpo() {
        EXPO.stop(0);
    }

    @BeforeEach
    void clear() {
        pushDeliveryRepository.deleteAll();
        RECEIPTS.clear();
        RECEIPT_REQUEST_SIZES.clear();
        RECEIPTS_DOWN.set(false);
        SEND_RESPONSE.set(TICKET_OK);
        userId = appUserRepository.saveAndFlush(
                new AppUser("social", "push-" + UUID.randomUUID() + "@test.com", "google", "sub-" + UUID.randomUUID(), Role.ROLE_USER)).getId();
    }

    @Test
    @DisplayName("ticket을 받으면 발송 기록을 DB에 남기고 첫 확인은 15분 뒤로 잡는다")
    void ticketIsStoredInDb() {
        Instant before = Instant.now();

        expoPushService.sendMessage(new PushSendRequest(userId, "ExpoPushToken[a]", "title", "body",
                NotificationType.EXPIRY_SOON, 42L));

        PushDelivery delivery = pushDeliveryRepository.findAll().get(0);
        assertThat(delivery.getStatus()).isEqualTo(PushDeliveryStatus.CHECKING);
        assertThat(delivery.getTicketId()).isEqualTo("ticket-from-stub");
        assertThat(delivery.getUserId()).isEqualTo(userId);
        assertThat(delivery.getNotificationId()).isEqualTo(42L);
        assertThat(delivery.getNextCheckAt()).isAfterOrEqualTo(before.plus(Duration.ofMinutes(15)));
    }

    @Test
    @DisplayName("커밋 후 콜백 안에서 같은 스레드로 발송해도 발송 기록을 저장한다")
    void ticketIsStoredWhenSentInsideAfterCommit() {
        transactionTemplate.executeWithoutResult(status -> TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        expoPushService.sendMessage(new PushSendRequest(userId, "ExpoPushToken[after-commit]",
                                "title", "body", NotificationType.EXPIRY_SOON, 42L));
                    }
                }));

        assertThat(pushDeliveryRepository.findAll()).extracting(PushDelivery::getTicketId)
                .containsExactly("ticket-from-stub");
    }

    @Test
    @DisplayName("커밋 후 콜백 안에서 발송해 DeviceNotRegistered를 받아도 기기 토큰을 지운다")
    void deviceTokenIsDeletedWhenSentInsideAfterCommit() {
        String token = "ExpoPushToken[gone-after-commit-" + System.nanoTime() + "]";
        deviceTokenRepository.save(new DeviceToken(userId, token));
        SEND_RESPONSE.set("{\"data\":{\"status\":\"error\",\"details\":{\"error\":\"DeviceNotRegistered\"}}}");

        transactionTemplate.executeWithoutResult(status -> TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        expoPushService.sendMessage(new PushSendRequest(userId, token, "title", "body",
                                NotificationType.EXPIRY_SOON, 42L));
                    }
                }));

        assertThat(deviceTokenRepository.findAllByUserId(userId)).extracting(DeviceToken::getToken).doesNotContain(token);
    }

    @Test
    @DisplayName("DB에 남은 확인 대상은 새로 뜬 서버도 이어서 확인하고, 300건 넘게 쌓여도 한 번에 모두 조회한다")
    void dueTicketsInDbAreAllChecked() {
        Instant past = Instant.now().minusSeconds(60);
        List<PushDelivery> rows = IntStream.range(0, 1200)
                .mapToObj(i -> due("t-" + i, past.minusSeconds(i)))
                .toList();
        pushDeliveryRepository.saveAll(rows);
        rows.forEach(row -> RECEIPTS.put(row.getTicketId(), "{\"status\":\"ok\"}"));

        expoPushService.checkReceipts();

        assertThat(pushDeliveryRepository.findAll())
                .extracting(PushDelivery::getStatus)
                .containsOnly(PushDeliveryStatus.DELIVERED);
        assertThat(RECEIPT_REQUEST_SIZES).containsExactly(1000, 200);
    }

    @Test
    @DisplayName("receipt가 아직 없으면 확인 중으로 두고 다음 확인을 뒤로 미룬다. 24시간이 지나면 만료한다")
    void pendingIsRescheduledAndOldIsExpired() {
        Instant now = Instant.now();
        PushDelivery fresh = pushDeliveryRepository.save(due("fresh", now.minus(Duration.ofMinutes(20))));
        PushDelivery old = pushDeliveryRepository.save(due("old", now.minus(Duration.ofHours(25))));

        expoPushService.checkReceipts();

        PushDelivery freshAfter = pushDeliveryRepository.findById(fresh.getId()).orElseThrow();
        assertThat(freshAfter.getStatus()).isEqualTo(PushDeliveryStatus.CHECKING);
        assertThat(freshAfter.getAttempts()).isEqualTo(1);
        assertThat(freshAfter.getNextCheckAt()).isAfter(now.plus(Duration.ofMinutes(14)));
        assertThat(pushDeliveryRepository.findById(old.getId()).orElseThrow().getStatus())
                .isEqualTo(PushDeliveryStatus.EXPIRED);
    }

    @Test
    @DisplayName("receipt가 DeviceNotRegistered면 실패로 기록하고 기기 토큰을 지운다")
    void deviceNotRegisteredFailsAndDeletesToken() {
        String token = "ExpoPushToken[gone-" + System.nanoTime() + "]";
        deviceTokenRepository.save(new DeviceToken(userId, token));
        PushDelivery delivery = PushDelivery.ticketed(userId, 1L, token, "title", "body", NotificationType.EXPIRY_SOON,
                "gone", Instant.now().minusSeconds(60), Instant.now().minusSeconds(1));
        pushDeliveryRepository.save(delivery);
        RECEIPTS.put("gone", "{\"status\":\"error\",\"details\":{\"error\":\"DeviceNotRegistered\"}}");

        expoPushService.checkReceipts();

        PushDelivery after = pushDeliveryRepository.findById(delivery.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(PushDeliveryStatus.FAILED);
        assertThat(after.getLastError()).isEqualTo("DeviceNotRegistered");
        assertThat(deviceTokenRepository.findAllByUserId(userId)).extracting(DeviceToken::getToken).doesNotContain(token);
    }

    @Test
    @DisplayName("receipt 조회가 실패해도 24시간이 지난 행은 만료하고, 나머지는 확인 중으로 남긴다")
    void lookupFailureStillExpiresOldRows() {
        Instant now = Instant.now();
        PushDelivery fresh = pushDeliveryRepository.save(due("fresh-down", now.minus(Duration.ofMinutes(20))));
        PushDelivery old = pushDeliveryRepository.save(due("old-down", now.minus(Duration.ofHours(25))));
        RECEIPTS_DOWN.set(true);

        expoPushService.checkReceipts();

        assertThat(pushDeliveryRepository.findById(fresh.getId()).orElseThrow().getStatus())
                .isEqualTo(PushDeliveryStatus.CHECKING);
        assertThat(pushDeliveryRepository.findById(old.getId()).orElseThrow().getStatus())
                .isEqualTo(PushDeliveryStatus.EXPIRED);
    }

    @Test
    @DisplayName("이미 끝난 발송 기록은 다시 완료 처리되지 않는다")
    void finishedRowIsNotFinishedTwice() {
        PushDelivery delivery = pushDeliveryRepository.save(due("twice", Instant.now()));

        int first = pushDeliveryRepository.finish(List.of(delivery.getId()), PushDeliveryStatus.FAILED, "MessageRateExceeded");
        int second = pushDeliveryRepository.finish(List.of(delivery.getId()), PushDeliveryStatus.FAILED, "MessageRateExceeded");

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
    }

    @Test
    @DisplayName("사용자가 삭제되면 발송 기록도 지워지고, 삭제된 사용자의 발송 기록은 저장되지 않는다")
    void deletedUserHasNoDeliveries() {
        pushDeliveryRepository.save(due("bye", Instant.now()));

        appUserService.deleteUser(userId, null);

        assertThat(pushDeliveryRepository.findAll()).isEmpty();
        expoPushService.sendMessage(new PushSendRequest(userId, "ExpoPushToken[late]", "title", "body",
                NotificationType.EXPIRY_SOON, 42L));
        assertThat(pushDeliveryRepository.findAll()).isEmpty();
    }

    private PushDelivery due(String ticketId, Instant ticketedAt) {
        return PushDelivery.ticketed(userId, 1L, "ExpoPushToken[" + ticketId + "]", "title", "body",
                NotificationType.EXPIRY_SOON, ticketId, ticketedAt, Instant.now().minusSeconds(1));
    }

    private static HttpServer startExpoStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/send", exchange -> respond(exchange, SEND_RESPONSE.get()));
            server.createContext("/receipts", exchange -> {
                if (RECEIPTS_DOWN.get()) {
                    exchange.getRequestBody().readAllBytes();
                    exchange.sendResponseHeaders(503, -1);
                    exchange.close();
                    return;
                }
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                StringBuilder data = new StringBuilder();
                int asked = 0;
                Matcher m = ID.matcher(body.substring(body.indexOf('[')));
                while (m.find()) {
                    asked++;
                    String receipt = RECEIPTS.get(m.group(1));
                    if (receipt != null) {
                        data.append(data.isEmpty() ? "" : ",").append('"').append(m.group(1)).append("\":").append(receipt);
                    }
                }
                RECEIPT_REQUEST_SIZES.add(asked);
                respond(exchange, "{\"data\":{" + data + "}}");
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
