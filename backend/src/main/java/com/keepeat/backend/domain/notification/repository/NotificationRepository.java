package com.keepeat.backend.domain.notification.repository;

import com.keepeat.backend.domain.notification.entity.Notification;
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

    @Query("SELECT n FROM Notification n WHERE n.userId = :userId AND (cast(:cursor as long) IS NULL OR n.id < :cursor) ORDER BY n.id DESC")
    List<Notification> findNotificationsByCursor(
            @Param("userId") Long userId,
            @Param("cursor") Long cursor,
            Pageable pageable
    );
}