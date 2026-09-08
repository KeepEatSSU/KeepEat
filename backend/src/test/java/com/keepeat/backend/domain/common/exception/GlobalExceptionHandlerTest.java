package com.keepeat.backend.domain.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/users/login");
        return request;
    }

    @Test
    void rateLimitResponseCarriesActualRemainingSecondsInRetryAfterHeader() {
        ResponseEntity<Map<String, String>> response =
                handler.handleRateLimitExceededException(new RateLimitExceededException(37L), request());

        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("37");
        assertThat(response.getBody()).containsEntry("code", ErrorCode.RATE_LIMIT_EXCEEDED.name());
    }

    @Test
    void plainRateLimitKeepEatExceptionStillFallsBackToSixtySeconds() {
        ResponseEntity<Map<String, String>> response =
                handler.handleKeepEatException(new KeepEatException(ErrorCode.RATE_LIMIT_EXCEEDED), request());

        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("60");
    }

    @Test
    void nonRateLimitErrorsDoNotCarryRetryAfterHeader() {
        ResponseEntity<Map<String, String>> response =
                handler.handleKeepEatException(new KeepEatException(ErrorCode.RECIPE_NOT_FOUND), request());

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getHeaders().getFirst("Retry-After")).isNull();
    }

    // 서브타입이 상위 KeepEatException 핸들러(고정 60초)가 아니라 전용 핸들러로
    // 디스패치되는 것까지 실제 예외 처리 경로로 고정한다
    @Test
    void rateLimitSubtypeIsRoutedToTheSpecificHandlerNotTheGenericFallback() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/rate-limited"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "37"));
    }

    @RestController
    static class ThrowingController {
        @GetMapping("/rate-limited")
        void boom() {
            throw new RateLimitExceededException(37L);
        }
    }
}
