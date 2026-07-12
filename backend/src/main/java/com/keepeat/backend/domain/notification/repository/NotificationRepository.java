package com.keepeat.backend.domain.notification.repository;

import com.keepeat.backend.domain.notification.entity.Notification;
import com.keepeat.backend.domain.notification.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    // 유저의 알림 히스토리를 최신순으로 가져오기
    List<Notification> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    // 유저 ID로 안 읽은 알림(isRead = false) 개수만 빠르게 세어오는 쿼리
    int countByUserIdAndIsReadFalse(Long userId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.userId = :userId AND n.isRead = false")
    int markAllAsReadByUserId(@Param("userId") Long userId);

    // 특정 타입의 안 읽은 알림만 한 번에 읽음 처리 (예: EXPIRY_SOON 전체 읽음)
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true " +
           "WHERE n.userId = :userId AND n.notificationType = :type AND n.isRead = false")
    int markAllAsReadByUserIdAndType(@Param("userId") Long userId, @Param("type") NotificationType type);

    boolean existsByDedupeKey(String dedupeKey);

    @Query("SELECT n FROM Notification n WHERE n.userId = :userId AND (:cursor IS NULL OR n.id < :cursor) ORDER BY n.id DESC")
    List<Notification> findNotificationsByCursor(
            @Param("userId") Long userId,
            @Param("cursor") Long cursor,
            Pageable pageable
    );

    // 단일 삭제: 본인 소유일 때만 삭제됨 (반환값으로 실제 삭제 여부 판단)
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.id = :id AND n.userId = :userId")
    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    // 일괄 삭제: ids 중 본인 소유인 것만 삭제 (남의 알림 ID 섞여 있어도 조용히 무시)
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.id IN :ids AND n.userId = :userId")
    int deleteAllByIdInAndUserId(@Param("ids") List<Long> ids, @Param("userId") Long userId);
}
