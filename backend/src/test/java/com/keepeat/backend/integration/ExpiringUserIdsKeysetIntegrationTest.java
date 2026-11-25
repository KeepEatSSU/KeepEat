package com.keepeat.backend.integration;

import com.keepeat.backend.domain.common.enums.StorageType;
import com.keepeat.backend.domain.user.entity.AppUser;
import com.keepeat.backend.domain.user.entity.Role;
import com.keepeat.backend.domain.user.repository.AppUserRepository;
import com.keepeat.backend.domain.useringredient.UserIngredient;
import com.keepeat.backend.domain.useringredient.UserIngredientRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ExpiringUserIdsKeysetIntegrationTest {

    @Autowired
    private UserIngredientRepository userIngredientRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final LocalDate today = LocalDate.of(2026, 11, 25);

    @Test
    @DisplayName("마지막 userId 다음부터 대상 유저를 한 번씩 순서대로 가져오고 범위 밖·탈퇴 유저는 뺀다")
    void keysetReturnsEachExpiringUserOnceInOrder() {
        long a = user();
        long b = user();
        long c = user();
        long outOfRange = user();
        long deleted = user();
        item(a, today.plusDays(1));
        item(a, today.plusDays(2));
        item(b, today);
        item(c, today.plusDays(3));
        item(outOfRange, today.plusDays(4));
        item(deleted, today.plusDays(1));
        jdbcTemplate.update("UPDATE app_user SET deleted_at = now() WHERE id = ?", deleted);

        List<Long> first = page(a - 1);
        List<Long> second = page(first.get(first.size() - 1));
        List<Long> third = page(second.get(second.size() - 1));

        assertThat(first).containsExactly(a, b);
        assertThat(second).containsExactly(c);
        assertThat(third).isEmpty();
    }

    private List<Long> page(long afterUserId) {
        return userIngredientRepository.findExpiringUserIdsAfter(
                today, today.plusDays(3), afterUserId, PageRequest.of(0, 2));
    }

    private long user() {
        return appUserRepository.saveAndFlush(new AppUser("social", "keyset-" + UUID.randomUUID() + "@test.com",
                "google", "sub-" + UUID.randomUUID(), Role.ROLE_USER)).getId();
    }

    private void item(long userId, LocalDate expiryDate) {
        userIngredientRepository.saveAndFlush(UserIngredient.builder()
                .userId(userId)
                .storageType(StorageType.냉장)
                .purchaseDate(today.minusDays(1))
                .expiryDate(expiryDate)
                .customName("우유")
                .build());
    }
}
