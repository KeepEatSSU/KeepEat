package com.keepeat.backend.domain.user.repository;

import com.keepeat.backend.domain.user.entity.EmailAuth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.time.LocalDateTime;

public interface EmailAuthRepository extends JpaRepository<EmailAuth, Long> {
    Optional<EmailAuth> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM EmailAuth e WHERE e.email = :email")
    Optional<EmailAuth> findWithLockByEmail(@Param("email") String email);

    void deleteAllByExpiredAtBefore(LocalDateTime now);
}
