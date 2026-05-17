package com.keepeat.backend.domain.user.repository;

import com.keepeat.backend.domain.user.entity.EmailAuth;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface EmailAuthRepository extends JpaRepository<EmailAuth, Long> {
    Optional<EmailAuth> findByEmail(String email);
}