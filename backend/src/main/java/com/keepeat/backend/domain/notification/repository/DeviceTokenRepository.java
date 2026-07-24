package com.keepeat.backend.domain.notification.repository;

import com.keepeat.backend.domain.notification.entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {
    // 유저의 모든 기기 토큰 가져오기 (알림 쏠 때 사용)
    List<DeviceToken> findAllByUserId(Long userId);

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO device_token (user_id, session_id, token, created_at)
            VALUES (:userId, :sessionId, :token, :createdAt)
            ON CONFLICT (token) DO UPDATE
            SET user_id = EXCLUDED.user_id,
                session_id = EXCLUDED.session_id
            """, nativeQuery = true)
    int upsertToken(
            @Param("userId") Long userId,
            @Param("sessionId") String sessionId,
            @Param("token") String token,
            @Param("createdAt") LocalDateTime createdAt
    );

    // 토큰 삭제 쿼리
    @Transactional
    void deleteByToken(String token);

    // 본인 소유 토큰만 삭제 (로그아웃 시 소유자 검증)
    @Transactional
    void deleteByUserIdAndToken(Long userId, String token);

    // 회원 탈퇴 시 해당 유저의 모든 푸시 토큰 즉시 정리 (탈퇴 후 푸시 송신 차단)
    @Transactional
    void deleteAllByUserId(Long userId);

    @Transactional
    void deleteAllBySessionId(String sessionId);
}
