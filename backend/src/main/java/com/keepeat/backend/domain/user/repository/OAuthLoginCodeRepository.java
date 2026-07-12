package com.keepeat.backend.domain.user.repository;

import com.keepeat.backend.domain.user.entity.OAuthLoginCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface OAuthLoginCodeRepository extends JpaRepository<OAuthLoginCode, Long> {
    Optional<OAuthLoginCode> findByCodeHash(String codeHash);

    void deleteAllByExpiresAtBefore(LocalDateTime expiresAt);
}
