package com.keepeat.backend.domain.notification.repository;

import com.keepeat.backend.domain.notification.entity.PushDelivery;
import com.keepeat.backend.domain.notification.entity.PushDeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface PushDeliveryRepository extends JpaRepository<PushDelivery, Long> {

    @Query(value = """
            SELECT * FROM push_delivery
            WHERE status = 'PENDING' AND next_send_at <= :now
            ORDER BY next_send_at, id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<PushDelivery> lockDueForSend(@Param("now") Instant now, @Param("limit") int limit);

    @Query(value = """
            SELECT * FROM push_delivery
            WHERE status = 'CHECKING' AND next_check_at <= :now
            ORDER BY next_check_at, id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<PushDelivery> lockDueForReceiptCheck(@Param("now") Instant now, @Param("limit") int limit);

    @Modifying
    @Transactional
    @Query("""
            UPDATE PushDelivery d SET d.status = com.keepeat.backend.domain.notification.entity.PushDeliveryStatus.CHECKING,
                d.ticketId = :ticketId, d.ticketedAt = :ticketedAt, d.nextCheckAt = :nextCheckAt, d.nextSendAt = null
            WHERE d.id = :id AND d.sendAttempts = :sendAttempts
                AND d.status = com.keepeat.backend.domain.notification.entity.PushDeliveryStatus.PENDING
            """)
    int markTicketed(@Param("id") Long id, @Param("sendAttempts") int sendAttempts, @Param("ticketId") String ticketId,
                     @Param("ticketedAt") Instant ticketedAt, @Param("nextCheckAt") Instant nextCheckAt);

    @Modifying
    @Transactional
    @Query("""
            UPDATE PushDelivery d SET d.status = com.keepeat.backend.domain.notification.entity.PushDeliveryStatus.PENDING,
                d.ticketId = null, d.nextCheckAt = null, d.nextSendAt = :nextSendAt, d.lastError = :error
            WHERE d.id = :id AND d.sendAttempts = :sendAttempts
                AND d.status IN (com.keepeat.backend.domain.notification.entity.PushDeliveryStatus.PENDING,
                com.keepeat.backend.domain.notification.entity.PushDeliveryStatus.CHECKING)
            """)
    int retrySend(@Param("id") Long id, @Param("sendAttempts") int sendAttempts, @Param("nextSendAt") Instant nextSendAt,
                  @Param("error") String error);

    @Modifying
    @Transactional
    @Query("""
            UPDATE PushDelivery d SET d.status = com.keepeat.backend.domain.notification.entity.PushDeliveryStatus.FAILED,
                d.lastError = :error, d.nextSendAt = null
            WHERE d.id = :id AND d.sendAttempts = :sendAttempts
                AND d.status = com.keepeat.backend.domain.notification.entity.PushDeliveryStatus.PENDING
            """)
    int failSend(@Param("id") Long id, @Param("sendAttempts") int sendAttempts, @Param("error") String error);

    @Modifying
    @Transactional
    @Query("""
            UPDATE PushDelivery d SET d.status = :status, d.lastError = :error, d.nextCheckAt = null, d.nextSendAt = null
            WHERE d.id IN :ids AND d.status IN (com.keepeat.backend.domain.notification.entity.PushDeliveryStatus.PENDING,
                com.keepeat.backend.domain.notification.entity.PushDeliveryStatus.CHECKING)
            """)
    int finish(@Param("ids") Collection<Long> ids, @Param("status") PushDeliveryStatus status, @Param("error") String error);

    @Modifying
    @Transactional
    @Query("DELETE FROM PushDelivery d WHERE d.createdAt < :cutoff")
    int deleteCreatedBefore(@Param("cutoff") Instant cutoff);

    @Transactional
    void deleteAllByUserId(Long userId);
}
