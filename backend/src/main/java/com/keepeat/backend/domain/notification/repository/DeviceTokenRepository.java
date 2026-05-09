package com.keepeat.backend.domain.notification.repository;

import com.keepeat.backend.domain.notification.entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {
    // 유저의 모든 기기 토큰 가져오기 (알림 쏠 때 사용)
    List<DeviceToken> findAllByUserId(Long userId);

    // 이미 존재하는 토큰인지 중복 검사용
    Optional<DeviceToken> findByToken(String token);

    // 토큰 삭제 쿼리
    void deleteByToken(String token);
}