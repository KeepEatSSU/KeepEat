package com.keepeat.backend.domain.user.repository;

import org.springframework.data.jpa.repository.Modifying;
import com.keepeat.backend.domain.user.entity.OAuthLoginCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.Optional;

public interface OAuthLoginCodeRepository extends JpaRepository<OAuthLoginCode, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM OAuthLoginCode c WHERE c.codeHash = :codeHash")
    Optional<OAuthLoginCode> findByCodeHashForUpdate(@Param("codeHash") String codeHash);

    @Modifying
    @Query("DELETE FROM OAuthLoginCode c WHERE c.expiresAt < :expiresAt")
    void deleteAllByExpiresAtBefore(@Param("expiresAt") LocalDateTime expiresAt);

    @Modifying
    @Query("DELETE FROM OAuthLoginCode c WHERE c.userId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
