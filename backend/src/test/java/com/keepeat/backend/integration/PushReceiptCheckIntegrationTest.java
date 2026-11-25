package com.keepeat.backend.integration;

import com.keepeat.backend.domain.notification.entity.DeviceToken;
import com.keepeat.backend.domain.notification.entity.NotificationType;
import com.keepeat.backend.domain.notification.entity.PushDelivery;
import com.keepeat.backend.domain.notification.entity.PushDeliveryStatus;
import com.keepeat.backend.domain.notification.repository.DeviceTokenRepository;
import com.keepeat.backend.domain.notification.repository.PushDeliveryRepository;
import com.keepeat.backend.domain.notification.service.ExpoPushService;
import com.keepeat.backend.domain.notification.service.NotificationService;
import com.keepeat.backend.domain.common.exception.KeepEatException;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
class PushReceiptCheckIntegrationTest {

    private static final HttpServer EXPO = startExpoStub();
    private static final Map<String, String> RECEIPTS = new ConcurrentHashMap<>();
    private static final List<Integer> RECEIPT_REQUEST_SIZES = new CopyOnWriteArrayList<>();
    private static final Pattern ID = Pattern.compile("\"([^\"]+)\"");
    private static final AtomicBoolean RECEIPTS_DOWN = new AtomicBoolean();
    private static final String TICKET_OK = "{\"status\":\"ok\",\"id\":\"ticket-from-stub\"}";
    private static final Pattern TO = Pattern.compile("\"to\":");
    private static final java.util.concurrent.atomic.AtomicReference<String> SEND_RESPONSE =
            new java.util.concurrent.atomic.AtomicReference<>(TICKET_OK);
    private static final java.util.concurrent.atomic.AtomicInteger SEND_STATUS = new java.util.concurrent.atomic.AtomicInteger(200);
    private static final List<String> SEND_BODIES = new CopyOnWriteArrayList<>();
    private static final java.util.concurrent.atomic.AtomicReference<String> SEND_DATA =
            new java.util.concurrent.atomic.AtomicReference<>();

    @Autowired
    private ExpoPushService expoPushService;

    @Autowired
    private PushDeliveryRepository pushDeliveryRepository;

    @MockitoSpyBean
    private DeviceTokenRepository deviceTokenRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private AppUserService appUserService;

    @Autowired
    private NotificationService notificationService;

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
        SEND_STATUS.set(200);
        SEND_BODIES.clear();
        SEND_DATA.set(null);
        userId = appUserRepository.saveAndFlush(
                new AppUser("social", "push-" + UUID.randomUUID() + "@test.com", "google", "sub-" + UUID.randomUUID(), Role.ROLE_USER)).getId();
    }

    @Test
    @DisplayName("발송 대기 기록을 보내 ticket을 받으면 확인 중으로 바꾸고 첫 확인은 15분 뒤로 잡는다")
    void pendingDeliveryIsSentAndTicketed() {
        Instant before = Instant.now();
        PushDelivery pending = pushDeliveryRepository.save(pending("ExpoPushToken[a]", before));

        expoPushService.sendDue();

        PushDelivery delivery = pushDeliveryRepository.findById(pending.getId()).orElseThrow();
        assertThat(delivery.getStatus()).isEqualTo(PushDeliveryStatus.CHECKING);
        assertThat(delivery.getTicketId()).isEqualTo("ticket-from-stub");
        assertThat(delivery.getSendAttempts()).isEqualTo(1);
        assertThat(delivery.getNextSendAt()).isNull();
        assertThat(delivery.getNextCheckAt()).isAfterOrEqualTo(before.plus(Duration.ofMinutes(15)));
        assertThat(SEND_BODIES).singleElement().asString()
                .contains("\"collapseId\":\"push-" + pending.getId() + "\"")
                .contains("\"tag\":\"push-" + pending.getId() + "\"");
    }

    @Test
    @DisplayName("알림을 저장하면 같은 트랜잭션에 발송 대기 기록이 남고, 커밋 뒤 발송 작업이 보내 확인 중으로 바뀐다")
    void notificationIsEnqueuedAndSentAfterCommit() throws InterruptedException {
        deviceTokenRepository.save(new DeviceToken(userId, "ExpoPushToken[e2e-" + System.nanoTime() + "]"));

        notificationService.sendNotification(userId, "title", "body", NotificationType.NOTICE, null);

        Instant deadline = Instant.now().plusSeconds(10);
        while (Instant.now().isBefore(deadline) && pushDeliveryRepository.findAll().stream()
                .anyMatch(d -> d.getStatus() == PushDeliveryStatus.PENDING)) {
            Thread.sleep(50);
        }
        assertThat(pushDeliveryRepository.findAll()).singleElement().satisfies(d -> {
            assertThat(d.getStatus()).isEqualTo(PushDeliveryStatus.CHECKING);
            assertThat(d.getUserId()).isEqualTo(userId);
            assertThat(d.getNotificationId()).isNotNull();
        });
    }

    @Test
    @DisplayName("Expo가 5xx면 발송 대기로 남기고 다음 시도를 뒤로 미룬다. 생성 24시간이 지났으면 실패로 끝낸다")
    void serverErrorIsRetriedLaterUntilRetention() {
        Instant now = Instant.now();
        PushDelivery fresh = pushDeliveryRepository.save(pending("ExpoPushToken[fresh]", now));
        PushDelivery old = pushDeliveryRepository.save(pending("ExpoPushToken[old]", now.minus(Duration.ofHours(25))));
        SEND_STATUS.set(503);

        expoPushService.sendDue();

        PushDelivery freshAfter = pushDeliveryRepository.findById(fresh.getId()).orElseThrow();
        assertThat(freshAfter.getStatus()).isEqualTo(PushDeliveryStatus.PENDING);
        assertThat(freshAfter.getNextSendAt()).isAfter(now);
        assertThat(freshAfter.getLastError()).isNotNull();
        assertThat(pushDeliveryRepository.findById(old.getId()).orElseThrow().getStatus())
                .isEqualTo(PushDeliveryStatus.FAILED);

        pushDeliveryRepository.retrySend(fresh.getId(), freshAfter.getSendAttempts(), now.plus(Duration.ofHours(1)), "503");
        SEND_STATUS.set(200);
        expoPushService.sendDue();
        assertThat(pushDeliveryRepository.findById(fresh.getId()).orElseThrow().getStatus())
                .isEqualTo(PushDeliveryStatus.PENDING);
    }

    @Test
    @DisplayName("다른 서버가 가져간 뒤 끝내지 못한 발송 대기 기록은 점유 시간이 지나면 다시 보낸다")
    void abandonedClaimIsSentAgain() {
        PushDelivery claimed = pending("ExpoPushToken[claimed]", Instant.now());
        claimed.claimSend(Instant.now().minusSeconds(1));
        pushDeliveryRepository.save(claimed);

        expoPushService.sendDue();

        PushDelivery after = pushDeliveryRepository.findById(claimed.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(PushDeliveryStatus.CHECKING);
        assertThat(after.getSendAttempts()).isEqualTo(2);
    }

    @Test
    @DisplayName("점유 시간이 지나 다른 작업이 다시 가져간 행은 이전 작업의 늦은 결과로 바뀌지 않는다")
    void staleClaimResultIsIgnored() {
        PushDelivery stale = pushDeliveryRepository.save(pending("ExpoPushToken[stale]", Instant.now()));
        PushDelivery claimed = pushDeliveryRepository.findById(stale.getId()).orElseThrow();
        claimed.claimSend(Instant.now().minusSeconds(1));
        pushDeliveryRepository.save(claimed);

        expoPushService.sendDue();

        assertThat(pushDeliveryRepository.retrySend(stale.getId(), 1, Instant.now(), "late")).isZero();
        assertThat(pushDeliveryRepository.failSend(stale.getId(), 1, "late")).isZero();
        PushDelivery after = pushDeliveryRepository.findById(stale.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(PushDeliveryStatus.CHECKING);
        assertThat(after.getTicketId()).isEqualTo("ticket-from-stub");
    }

    @Test
    @DisplayName("receipt MessageRateExceeded로 되돌아온 행도 생성 24시간이 지났으면 보내지 않고 실패로 끝낸다")
    void requeuedRowPastRetentionIsNotSent() {
        PushDelivery old = pushDeliveryRepository.save(pending("ExpoPushToken[old-requeued]", Instant.now().minus(Duration.ofHours(25))));

        expoPushService.sendDue();

        assertThat(pushDeliveryRepository.findById(old.getId()).orElseThrow().getStatus()).isEqualTo(PushDeliveryStatus.FAILED);
        assertThat(SEND_BODIES).isEmpty();
    }

    @Test
    @DisplayName("Expo가 400이면 재시도하지 않고 실패로 끝낸다")
    void clientErrorFails() {
        PushDelivery pending = pushDeliveryRepository.save(pending("ExpoPushToken[bad]", Instant.now()));
        SEND_STATUS.set(400);

        expoPushService.sendDue();

        PushDelivery after = pushDeliveryRepository.findById(pending.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(PushDeliveryStatus.FAILED);
        assertThat(after.getLastError()).isEqualTo("HTTP 400");
    }

    @Test
    @DisplayName("ticket이 DeviceNotRegistered면 실패로 기록하고 기기 토큰을 지운다")
    void ticketDeviceNotRegisteredDeletesToken() {
        String token = "ExpoPushToken[gone-ticket-" + System.nanoTime() + "]";
        deviceTokenRepository.save(new DeviceToken(userId, token));
        PushDelivery pending = pushDeliveryRepository.save(pending(token, Instant.now()));
        SEND_RESPONSE.set("{\"status\":\"error\",\"details\":{\"error\":\"DeviceNotRegistered\"}}");

        expoPushService.sendDue();

        assertThat(pushDeliveryRepository.findById(pending.getId()).orElseThrow().getStatus())
                .isEqualTo(PushDeliveryStatus.FAILED);
        assertThat(deviceTokenRepository.findAllByUserId(userId)).extracting(DeviceToken::getToken).doesNotContain(token);
    }

    @Test
    @DisplayName("점유한 발송 대기 기록을 한 요청으로 묶어 보내고 ticket을 요청 순서대로 각 행에 대응시킨다")
    void claimedRowsAreSentInOneRequest() {
        String gone = "ExpoPushToken[gone-batch-" + System.nanoTime() + "]";
        deviceTokenRepository.save(new DeviceToken(userId, gone));
        PushDelivery first = pushDeliveryRepository.save(pending("ExpoPushToken[b1]", Instant.now()));
        PushDelivery second = pushDeliveryRepository.save(pending(gone, Instant.now()));
        PushDelivery third = pushDeliveryRepository.save(pending("ExpoPushToken[b3]", Instant.now()));
        SEND_DATA.set("[{\"status\":\"ok\",\"id\":\"t-1\"},"
                + "{\"status\":\"error\",\"details\":{\"error\":\"DeviceNotRegistered\"}},"
                + "{\"status\":\"ok\",\"id\":\"t-3\"}]");

        expoPushService.sendDue();

        assertThat(SEND_BODIES).singleElement().asString()
                .contains("ExpoPushToken[b1]").contains(gone).contains("ExpoPushToken[b3]");
        assertThat(pushDeliveryRepository.findById(first.getId()).orElseThrow().getTicketId()).isEqualTo("t-1");
        assertThat(pushDeliveryRepository.findById(second.getId()).orElseThrow().getStatus()).isEqualTo(PushDeliveryStatus.FAILED);
        assertThat(pushDeliveryRepository.findById(third.getId()).orElseThrow().getTicketId()).isEqualTo("t-3");
        assertThat(deviceTokenRepository.findAllByUserId(userId)).extracting(DeviceToken::getToken).doesNotContain(gone);
    }

    @Test
    @DisplayName("한 행의 ticket 처리가 실패해도 같은 묶음의 나머지 행은 ticket을 저장한다")
    void ticketHandlingFailureDoesNotStopOtherRows() {
        String broken = "ExpoPushToken[broken-" + System.nanoTime() + "]";
        PushDelivery first = pushDeliveryRepository.save(pending(broken, Instant.now()));
        PushDelivery second = pushDeliveryRepository.save(pending("ExpoPushToken[after-broken]", Instant.now()));
        SEND_DATA.set("[{\"status\":\"error\",\"details\":{\"error\":\"DeviceNotRegistered\"}},"
                + "{\"status\":\"ok\",\"id\":\"t-after\"}]");
        doThrow(new IllegalStateException("db down")).when(deviceTokenRepository).deleteByToken(broken);

        expoPushService.sendDue();

        assertThat(pushDeliveryRepository.findById(first.getId()).orElseThrow().getStatus()).isEqualTo(PushDeliveryStatus.PENDING);
        PushDelivery after = pushDeliveryRepository.findById(second.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(PushDeliveryStatus.CHECKING);
        assertThat(after.getTicketId()).isEqualTo("t-after");
    }

    @Test
    @DisplayName("ticket 수가 요청 건수와 다르면 묶인 행을 모두 발송 대기로 되돌린다")
    void ticketCountMismatchRetriesAll() {
        PushDelivery first = pushDeliveryRepository.save(pending("ExpoPushToken[m1]", Instant.now()));
        PushDelivery second = pushDeliveryRepository.save(pending("ExpoPushToken[m2]", Instant.now()));
        SEND_DATA.set("[{\"status\":\"ok\",\"id\":\"t-only\"}]");

        expoPushService.sendDue();

        assertThat(pushDeliveryRepository.findAllById(List.of(first.getId(), second.getId()))).allSatisfy(d -> {
            assertThat(d.getStatus()).isEqualTo(PushDeliveryStatus.PENDING);
            assertThat(d.getTicketId()).isNull();
            assertThat(d.getLastError()).isEqualTo("InvalidResponse");
        });
    }

    @Test
    @DisplayName("receipt가 MessageRateExceeded면 같은 발송 기록을 발송 대기로 되돌린다")
    void receiptRateExceededRequeues() {
        PushDelivery delivery = pushDeliveryRepository.save(due("rate", Instant.now().minusSeconds(60)));
        RECEIPTS.put("rate", "{\"status\":\"error\",\"details\":{\"error\":\"MessageRateExceeded\"}}");

        expoPushService.checkReceipts();

        PushDelivery after = pushDeliveryRepository.findById(delivery.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(PushDeliveryStatus.PENDING);
        assertThat(after.getTicketId()).isNull();
        assertThat(after.getNextSendAt()).isNotNull();
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
        assertThatThrownBy(() -> notificationService.sendNotification(userId, "title", "body", NotificationType.NOTICE, null))
                .isInstanceOf(KeepEatException.class);
        assertThat(pushDeliveryRepository.findAll()).isEmpty();
    }

    private PushDelivery pending(String token, Instant createdAt) {
        return PushDelivery.pending(userId, 1L, token, "title", "body", NotificationType.EXPIRY_SOON, createdAt);
    }

    private PushDelivery due(String ticketId, Instant ticketedAt) {
        return PushDelivery.ticketed(userId, 1L, "ExpoPushToken[" + ticketId + "]", "title", "body",
                NotificationType.EXPIRY_SOON, ticketId, ticketedAt, Instant.now().minusSeconds(1));
    }

    private static HttpServer startExpoStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/send", exchange -> {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                SEND_BODIES.add(body);
                if (SEND_STATUS.get() != 200) {
                    exchange.sendResponseHeaders(SEND_STATUS.get(), -1);
                    exchange.close();
                    return;
                }
                if (SEND_DATA.get() != null) {
                    respond(exchange, "{\"data\":" + SEND_DATA.get() + "}");
                    return;
                }
                List<String> tickets = new ArrayList<>();
                Matcher to = TO.matcher(body);
                while (to.find()) {
                    int i = tickets.size();
                    tickets.add(i == 0 ? SEND_RESPONSE.get() : SEND_RESPONSE.get().replace("ticket-from-stub", "ticket-from-stub-" + i));
                }
                respond(exchange, "{\"data\":[" + String.join(",", tickets) + "]}");
            });
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
