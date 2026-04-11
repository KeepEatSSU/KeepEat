package com.keepeat.backend.domain.user.repository;

import com.keepeat.backend.domain.user.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

// JpaRepository<엔티티 클래스, PK 타입>
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    // 이메일로 유저를 찾는 메서드
    Optional<AppUser> findByEmail(String email);

    // 이미 존재하는 이메일인지 확인하는 메서드
    boolean existsByEmail(String email);
}