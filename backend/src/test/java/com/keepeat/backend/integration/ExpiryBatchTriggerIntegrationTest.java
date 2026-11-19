package com.keepeat.backend.integration;

import com.keepeat.backend.domain.notification.scheduler.IngredientExpiryScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles({"test", "perf"})
class ExpiryBatchTriggerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private IngredientExpiryScheduler ingredientExpiryScheduler;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    @DisplayName("perf 프로필에서는 인증 없이 트리거할 수 있고, 배치는 요청 스레드가 아닌 스케줄러 스레드에서 실행된다")
    void triggerRunsBatchOnSchedulerThread() throws Exception {
        AtomicReference<String> threadName = new AtomicReference<>();
        CountDownLatch executed = new CountDownLatch(1);
        doAnswer(invocation -> {
            threadName.set(Thread.currentThread().getName());
            executed.countDown();
            return null;
        }).when(ingredientExpiryScheduler).checkExpiringIngredients();

        mockMvc.perform(post("/api/internal/perf/expiry-batch"))
                .andExpect(status().isAccepted());

        // Mockito는 answer 실행 전에 호출을 기록한다. 호출 기록이 아니라 answer가 끝난 신호를 기다린 뒤 검사한다
        assertThat(executed.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(threadName.get()).startsWith("scheduling-");
    }

    @Test
    @DisplayName("perf 프로필에서는 Prometheus 지표가 노출된다")
    void prometheusEndpointIsExposedInPerfProfile() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk());
    }
}
