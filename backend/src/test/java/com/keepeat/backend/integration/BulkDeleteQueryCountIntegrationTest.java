package com.keepeat.backend.integration;

import com.keepeat.backend.domain.user.entity.UserSession;
import com.keepeat.backend.domain.user.repository.UserSessionRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BulkDeleteQueryCountIntegrationTest {

    @Autowired
    private UserSessionRepository userSessionRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("유저 세션 일괄 삭제와 만료 세션 정리는 행 수와 무관하게 SQL DELETE 한 번으로 나간다")
    void bulkDeletesRunOneStatement() {
        long userId = 987_654_321L;
        Instant now = Instant.now();
        for (int i = 0; i < 3; i++) {
            userSessionRepository.save(UserSession.create(userId, "hash-" + i, now.plus(Duration.ofDays(1))));
            userSessionRepository.save(UserSession.create(userId + 1, "expired-" + i, now.minus(Duration.ofDays(1))));
        }
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);

        statistics.clear();
        userSessionRepository.deleteAllByUserId(userId);
        long byUser = statistics.getPrepareStatementCount();

        statistics.clear();
        userSessionRepository.deleteAllByExpiresAtBefore(now);
        long expired = statistics.getPrepareStatementCount();
        statistics.setStatisticsEnabled(false);

        assertThat(byUser).isEqualTo(1);
        assertThat(expired).isEqualTo(1);
        assertThat(userSessionRepository.findAll()).noneMatch(s -> s.getUserId().equals(userId) || s.getUserId().equals(userId + 1));
    }
}
