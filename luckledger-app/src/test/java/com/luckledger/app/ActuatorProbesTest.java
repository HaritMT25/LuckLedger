package com.luckledger.app;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Confirms the actuator probes are wired the way the deployment expects: {@code /actuator/health} is
 * exposed and anonymous (via SecurityConfig's {@code anyRequest().permitAll()}), reporting {@code UP};
 * while a sensitive endpoint that was deliberately NOT exposed ({@code /actuator/env}) is absent (404).
 * The app's own {@code /api/health} endpoint is a separate concern and stays untouched.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ActuatorProbesTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    private final TestRestTemplate rest;

    ActuatorProbesTest(TestRestTemplate rest) {
        this.rest = rest;
    }

    @Test
    void healthProbeIsExposedAnonymouslyAndReportsUp() {
        ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void unexposedEndpointIsNotReachable() {
        ResponseEntity<String> response = rest.getForEntity("/actuator/env", String.class);

        // env is not in management.endpoints.web.exposure.include, so it is not mapped at all.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
