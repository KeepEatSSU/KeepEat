package com.keepeat.backend.domain.user.repository;

import com.keepeat.backend.domain.user.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

// JpaRepository<엔티티 클래스, PK 타입>
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmailIgnoreCase(String email);

    Optional<AppUser> findByProviderAndProviderId(String provider, String providerId);

    boolean existsByEmailIgnoreCase(String email);
}
