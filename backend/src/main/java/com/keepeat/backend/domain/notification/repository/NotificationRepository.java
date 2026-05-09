package com.keepeat.backend.domain.notification.repository;

import com.keepeat.backend.domain.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    // 유저의 알림 히스토리를 최신순으로 가져오기
    List<Notification> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    // 유저 ID로 안 읽은 알림(isRead = false) 개수만 빠르게 세어오는 쿼리
    int countByUserIdAndIsReadFalse(Long userId);
}