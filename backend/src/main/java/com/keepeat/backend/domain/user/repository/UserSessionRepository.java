package com.keepeat.backend.domain.user.repository;

import org.springframework.data.jpa.repository.Modifying;
import com.keepeat.backend.domain.user.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSession, String> {
    Optional<UserSession> findByIdAndUserId(String id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM UserSession s WHERE s.id = :id AND s.userId = :userId")
    Optional<UserSession> findByIdAndUserIdForUpdate(@Param("id") String id, @Param("userId") Long userId);

    void deleteByIdAndUserId(String id, Long userId);

    @Modifying
    @Query("DELETE FROM UserSession s WHERE s.userId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM UserSession s WHERE s.expiresAt < :now")
    void deleteAllByExpiresAtBefore(@Param("now") Instant now);
}
