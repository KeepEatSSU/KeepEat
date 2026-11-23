package com.keepeat.backend.domain.notification.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "push_delivery")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "notification_id")
    private Long notificationId;

    @Column(nullable = false)
    private String token;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 1000)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false)
    private NotificationType notificationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PushDeliveryStatus status;

    @Column(name = "ticket_id", unique = true)
    private String ticketId;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "send_attempts", nullable = false)
    private int sendAttempts;

    @Column(name = "next_send_at", columnDefinition = "timestamp with time zone")
    private Instant nextSendAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "ticketed_at", columnDefinition = "timestamp with time zone")
    private Instant ticketedAt;

    @Column(name = "next_check_at", columnDefinition = "timestamp with time zone")
    private Instant nextCheckAt;

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;

    public static PushDelivery pending(Long userId, Long notificationId, String token, String title, String body,
                                       NotificationType notificationType, Instant now) {
        PushDelivery delivery = new PushDelivery();
        delivery.userId = userId;
        delivery.notificationId = notificationId;
        delivery.token = token;
        delivery.title = title;
        delivery.body = body;
        delivery.notificationType = notificationType;
        delivery.status = PushDeliveryStatus.PENDING;
        delivery.nextSendAt = now;
        delivery.createdAt = now;
        return delivery;
    }

    public static PushDelivery ticketed(Long userId, Long notificationId, String token, String title, String body,
                                        NotificationType notificationType, String ticketId, Instant now,
                                        Instant firstCheckAt) {
        PushDelivery delivery = new PushDelivery();
        delivery.userId = userId;
        delivery.notificationId = notificationId;
        delivery.token = token;
        delivery.title = title;
        delivery.body = body;
        delivery.notificationType = notificationType;
        delivery.status = PushDeliveryStatus.CHECKING;
        delivery.ticketId = ticketId;
        delivery.ticketedAt = now;
        delivery.nextCheckAt = firstCheckAt;
        delivery.createdAt = now;
        return delivery;
    }

    public void claimSend(Instant leaseUntil) {
        this.sendAttempts++;
        this.nextSendAt = leaseUntil;
    }

    public void claimReceiptCheck(Instant nextCheckAt) {
        this.attempts++;
        this.nextCheckAt = nextCheckAt;
    }
}
