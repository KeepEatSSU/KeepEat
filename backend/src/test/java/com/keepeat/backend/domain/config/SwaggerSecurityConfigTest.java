package com.keepeat.backend.domain.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SwaggerSecurityConfigTest.TestController.class)
@Import({
        SwaggerSecurityConfig.class,
        PasswordConfig.class,
        SwaggerSecurityConfigTest.TestController.class
})
@ImportAutoConfiguration({
        SecurityAutoConfiguration.class,
        ServletWebSecurityAutoConfiguration.class
})
@ActiveProfiles("demo")
@TestPropertySource(properties = {
        "app.security.swagger.username=docs-user",
        "app.security.swagger.password=docs-password-with-strong-length"
})
class SwaggerSecurityConfigTest {

    private static final String USERNAME = "docs-user";
    private static final String PASSWORD = "docs-password-with-strong-length";

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(strings = {"/swagger-ui/test-resource", "/v3/api-docs/test-resource"})
    void swaggerEndpointsRequireBasicAuthentication(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                        HttpHeaders.WWW_AUTHENTICATE,
                        containsString("Basic realm=\"KeepEat Swagger\"")
                ));
    }

    @Test
    void swaggerEndpointRejectsWrongCredentials() throws Exception {
        mockMvc.perform(get("/swagger-ui/test-resource")
                        .with(remoteAddress("192.0.2.10"))
                        .with(httpBasic(USERNAME, "wrong-password")))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/swagger-ui/test-resource", "/v3/api-docs/test-resource"})
    void swaggerEndpointsAcceptConfiguredCredentials(String path) throws Exception {
        mockMvc.perform(get(path)
                        .with(remoteAddress("192.0.2.11"))
                        .with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().string("swagger"));
    }

    @Test
    void repeatedFailuresBlockClientBeforeCorrectCredentialsCanBeRetried() throws Exception {
        String clientAddress = "192.0.2.20";

        for (int attempt = 1; attempt < 5; attempt++) {
            mockMvc.perform(get("/swagger-ui/test-resource")
                            .with(remoteAddress(clientAddress))
                            .with(httpBasic(USERNAME, "wrong-password-" + attempt)))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(get("/swagger-ui/test-resource")
                        .with(remoteAddress(clientAddress))
                        .with(httpBasic(USERNAME, "wrong-password-5")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(content().json("""
                        {
                          "code": "SWAGGER_AUTH_RATE_LIMITED",
                          "message": "Too many authentication attempts"
                        }
                        """));

        mockMvc.perform(get("/swagger-ui/test-resource")
                        .with(remoteAddress(clientAddress))
                        .with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(get("/swagger-ui/test-resource")
                        .with(remoteAddress("192.0.2.21"))
                        .with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void successfulAuthenticationClearsPreviousFailures() throws Exception {
        String clientAddress = "192.0.2.30";

        for (int attempt = 1; attempt < 5; attempt++) {
            mockMvc.perform(get("/swagger-ui/test-resource")
                            .with(remoteAddress(clientAddress))
                            .with(httpBasic(USERNAME, "wrong-before-success-" + attempt)))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(get("/swagger-ui/test-resource")
                        .with(remoteAddress(clientAddress))
                        .with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk());

        for (int attempt = 1; attempt < 5; attempt++) {
            mockMvc.perform(get("/swagger-ui/test-resource")
                            .with(remoteAddress(clientAddress))
                            .with(httpBasic(USERNAME, "wrong-after-success-" + attempt)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void forwardedForHeaderCannotBypassClientRateLimit() throws Exception {
        String clientAddress = "192.0.2.40";

        for (int attempt = 1; attempt <= 5; attempt++) {
            var result = mockMvc.perform(get("/swagger-ui/test-resource")
                    .with(remoteAddress(clientAddress))
                    .header("X-Forwarded-For", "198.51.100." + attempt)
                    .with(httpBasic(USERNAME, "wrong-password-" + attempt)));

            if (attempt < 5) {
                result.andExpect(status().isUnauthorized());
            } else {
                result.andExpect(status().isTooManyRequests());
            }
        }
    }

    @Test
    void swaggerResponsesPreventIndexingAndReferrerLeakage() throws Exception {
        mockMvc.perform(get("/swagger-ui/test-resource")
                        .with(remoteAddress("192.0.2.50"))
                        .with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Robots-Tag", "noindex, nofollow, noarchive"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    private static RequestPostProcessor remoteAddress(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    @RestController
    static class TestController {

        @GetMapping({"/swagger-ui/test-resource", "/v3/api-docs/test-resource"})
        String swaggerResource() {
            return "swagger";
        }
    }
}
