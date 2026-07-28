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
        "app.security.swagger.password=docs-password"
})
class SwaggerSecurityConfigTest {

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
                        .with(httpBasic("docs-user", "wrong-password")))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/swagger-ui/test-resource", "/v3/api-docs/test-resource"})
    void swaggerEndpointsAcceptConfiguredCredentials(String path) throws Exception {
        mockMvc.perform(get(path)
                        .with(httpBasic("docs-user", "docs-password")))
                .andExpect(status().isOk())
                .andExpect(content().string("swagger"));
    }

    @RestController
    static class TestController {

        @GetMapping({"/swagger-ui/test-resource", "/v3/api-docs/test-resource"})
        String swaggerResource() {
            return "swagger";
        }
    }
}
