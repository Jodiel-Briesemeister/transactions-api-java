package br.com.jodiel.transactionsapi.presentation;

import com.fasterxml.jackson.databind.JsonNode;
import br.com.jodiel.transactionsapi.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks what the actuator publishes. Spring Boot switches metrics export off in tests, which would
 * hide the Prometheus endpoint, so this class opts back in with {@code @AutoConfigureObservability}.
 */
@AutoConfigureObservability
class ActuatorExposureIntegrationTest extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate rest;

    private String accessToken() {
        ResponseEntity<JsonNode> response = rest.postForEntity("/auth/register", Map.of(
                "name", "Actuator User",
                "email", "actuator-" + UUID.randomUUID() + "@example.com",
                "password", "Str0ng!Pass"
        ), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = response.getBody();
        assertThat(body).as("response body").isNotNull();
        return body.get("accessToken").asText();
    }

    @Test
    @DisplayName("health and prometheus are reachable without a token")
    void healthAndPrometheusArePublic() {
        assertThat(rest.getForEntity("/actuator/health", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(rest.getForEntity("/actuator/prometheus", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("metrics and info are not exposed, even to an authenticated caller")
    void otherEndpointsAreNotExposed() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken());

        // Authenticated, so a 404 proves the endpoint is gone rather than merely protected.
        for (String path : List.of("/actuator/metrics", "/actuator/info")) {
            ResponseEntity<String> response = rest.exchange(path, HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);
            assertThat(response.getStatusCode()).as(path).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
