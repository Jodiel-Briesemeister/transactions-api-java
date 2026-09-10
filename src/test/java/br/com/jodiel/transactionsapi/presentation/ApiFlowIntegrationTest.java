package br.com.jodiel.transactionsapi.presentation;

import com.fasterxml.jackson.databind.JsonNode;
import br.com.jodiel.transactionsapi.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the real HTTP API end to end: register, deposit, withdraw, transfer, list, log out.
 * Covers the wiring that unit tests cannot see — security filters, JSON binding, status codes.
 */
class ApiFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate rest;

    private String uniqueEmail() {
        return "flow-" + UUID.randomUUID() + "@example.com";
    }

    private HttpEntity<Object> authed(Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    private String register(String email) {
        ResponseEntity<JsonNode> response = rest.postForEntity("/auth/register", Map.of(
                "name", "Flow User",
                "email", email,
                "password", "Str0ng!Pass",
                "phone", "+5511999999999"
        ), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        return response.getBody().get("accessToken").asText();
    }

    private long balanceOf(String token) {
        ResponseEntity<JsonNode> response = rest.exchange("/transactions/balance", HttpMethod.GET,
                authed(null, token), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().get("balance").asLong();
    }

    @Test
    @DisplayName("register opens an account with a zero balance")
    void registerOpensAccount() {
        String token = register(uniqueEmail());
        assertThat(balanceOf(token)).isZero();
    }

    @Test
    @DisplayName("deposit then withdraw moves the balance in both directions")
    void depositAndWithdraw() {
        String token = register(uniqueEmail());

        ResponseEntity<Void> deposit = rest.exchange("/transactions/deposit", HttpMethod.POST,
                authed(Map.of("amount", 1_000), token), Void.class);
        assertThat(deposit.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(balanceOf(token)).isEqualTo(1_000L);

        ResponseEntity<Void> withdraw = rest.exchange("/transactions/withdraw", HttpMethod.POST,
                authed(Map.of("amount", 300), token), Void.class);
        assertThat(withdraw.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(balanceOf(token)).isEqualTo(700L);
    }

    @Test
    @DisplayName("a withdrawal larger than the balance is refused and leaves the balance untouched")
    void refusesOverdraft() {
        String token = register(uniqueEmail());
        rest.exchange("/transactions/deposit", HttpMethod.POST,
                authed(Map.of("amount", 100), token), Void.class);

        ResponseEntity<JsonNode> response = rest.exchange("/transactions/withdraw", HttpMethod.POST,
                authed(Map.of("amount", 500), token), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().get("message").asText()).isEqualTo("Insufficient balance");
        assertThat(balanceOf(token)).isEqualTo(100L);
    }

    @Test
    @DisplayName("a transfer debits the sender and credits the recipient")
    void transfersBetweenUsers() {
        String senderEmail = uniqueEmail();
        String recipientEmail = uniqueEmail();
        String senderToken = register(senderEmail);
        String recipientToken = register(recipientEmail);

        rest.exchange("/transactions/deposit", HttpMethod.POST,
                authed(Map.of("amount", 1_000), senderToken), Void.class);

        ResponseEntity<Void> transfer = rest.exchange("/transactions/transfer", HttpMethod.POST,
                authed(Map.of("recipientEmail", recipientEmail, "amount", 400), senderToken), Void.class);

        assertThat(transfer.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(balanceOf(senderToken)).isEqualTo(600L);
        assertThat(balanceOf(recipientToken)).isEqualTo(400L);
    }

    @Test
    @DisplayName("the transaction list accepts the lowercase type filter used by the Node API")
    void listsTransactionsWithLowercaseFilter() {
        String token = register(uniqueEmail());
        rest.exchange("/transactions/deposit", HttpMethod.POST,
                authed(Map.of("amount", 100), token), Void.class);
        rest.exchange("/transactions/withdraw", HttpMethod.POST,
                authed(Map.of("amount", 40), token), Void.class);

        ResponseEntity<JsonNode> all = rest.exchange("/transactions", HttpMethod.GET,
                authed(null, token), JsonNode.class);
        assertThat(all.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(all.getBody()).hasSize(2);

        ResponseEntity<JsonNode> deposits = rest.exchange("/transactions?type=deposit", HttpMethod.GET,
                authed(null, token), JsonNode.class);
        assertThat(deposits.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(deposits.getBody()).hasSize(1);
        assertThat(deposits.getBody().get(0).get("type").asText()).isEqualTo("DEPOSIT");
        assertThat(deposits.getBody().get(0).get("amount").asLong()).isEqualTo(100L);
    }

    @Test
    @DisplayName("profile can be read and updated, and never exposes the password hash")
    void readsAndUpdatesProfile() {
        String email = uniqueEmail();
        String token = register(email);

        ResponseEntity<JsonNode> profile = rest.exchange("/user/profile", HttpMethod.GET,
                authed(null, token), JsonNode.class);
        assertThat(profile.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(profile.getBody().get("email").asText()).isEqualTo(email);
        assertThat(profile.getBody().has("passwordHash")).isFalse();

        ResponseEntity<JsonNode> updated = rest.exchange("/user/profile", HttpMethod.PATCH,
                authed(Map.of("name", "Renamed User"), token), JsonNode.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().get("name").asText()).isEqualTo("Renamed User");
    }

    @Test
    @DisplayName("login returns fresh tokens and logout revokes the access token")
    void loginAndLogout() {
        String email = uniqueEmail();
        register(email);

        ResponseEntity<JsonNode> login = rest.postForEntity("/auth/login",
                Map.of("email", email, "password", "Str0ng!Pass"), JsonNode.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);

        String accessToken = login.getBody().get("accessToken").asText();
        String refreshToken = login.getBody().get("refreshToken").asText();
        assertThat(balanceOf(accessToken)).isZero();

        ResponseEntity<Void> logout = rest.exchange("/auth/logout", HttpMethod.POST,
                authed(Map.of("refreshToken", refreshToken), accessToken), Void.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // The blacklisted access token must stop working immediately.
        ResponseEntity<JsonNode> afterLogout = rest.exchange("/transactions/balance", HttpMethod.GET,
                authed(null, accessToken), JsonNode.class);
        assertThat(afterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("refresh rotates the token pair and burns the old refresh token")
    void refreshRotatesTokens() {
        String email = uniqueEmail();
        register(email);

        ResponseEntity<JsonNode> login = rest.postForEntity("/auth/login",
                Map.of("email", email, "password", "Str0ng!Pass"), JsonNode.class);
        String refreshToken = login.getBody().get("refreshToken").asText();

        ResponseEntity<JsonNode> refreshed = rest.postForEntity("/auth/refresh",
                Map.of("refreshToken", refreshToken), JsonNode.class);
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refreshed.getBody().get("refreshToken").asText()).isNotEqualTo(refreshToken);

        ResponseEntity<JsonNode> reuse = rest.postForEntity("/auth/refresh",
                Map.of("refreshToken", refreshToken), JsonNode.class);
        assertThat(reuse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("a duplicate registration is a 409, not a 500")
    void duplicateRegistrationIsConflict() {
        String email = uniqueEmail();
        register(email);

        ResponseEntity<JsonNode> second = rest.postForEntity("/auth/register", Map.of(
                "name", "Flow User", "email", email, "password", "Str0ng!Pass"), JsonNode.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("an invalid body is a 400 listing the offending fields")
    void invalidBodyIsBadRequest() {
        ResponseEntity<JsonNode> response = rest.postForEntity("/auth/register", Map.of(
                "name", "X", "email", "not-an-email", "password", "short"), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("message").asText()).isEqualTo("Validation error");
        assertThat(response.getBody().get("errors").fieldNames())
                .toIterable().contains("email", "password", "name");
    }

    @Test
    @DisplayName("a protected route without a token is 401")
    void requiresAuthentication() {
        ResponseEntity<JsonNode> response =
                rest.getForEntity("/transactions/balance", JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("a garbage token is 401, not 500")
    void rejectsMalformedToken() {
        ResponseEntity<JsonNode> response = rest.exchange("/transactions/balance", HttpMethod.GET,
                authed(null, "not.a.jwt"), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("an unknown route answers 404 instead of being swallowed into a 500")
    void unknownRouteIsNotFound() {
        String token = register(uniqueEmail());

        ResponseEntity<JsonNode> response = rest.exchange("/does-not-exist", HttpMethod.GET,
                authed(null, token), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("deactivating the account blocks a later login with 403 ACCOUNT_INACTIVE")
    void deactivateThenLogin() {
        String email = uniqueEmail();
        String token = register(email);

        ResponseEntity<Void> deactivate = rest.exchange("/user/account", HttpMethod.DELETE,
                authed(null, token), Void.class);
        assertThat(deactivate.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<JsonNode> login = rest.postForEntity("/auth/login",
                Map.of("email", email, "password", "Str0ng!Pass"), JsonNode.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(login.getBody().get("code").asText()).isEqualTo("ACCOUNT_INACTIVE");

        ResponseEntity<JsonNode> reactivated = rest.postForEntity("/auth/reactivate",
                Map.of("email", email, "password", "Str0ng!Pass"), JsonNode.class);
        assertThat(reactivated.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("health reports the state of every dependency")
    void healthReportsDependencies() {
        ResponseEntity<JsonNode> liveness = rest.getForEntity("/health", JsonNode.class);
        assertThat(liveness.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(liveness.getBody().get("status").asText()).isEqualTo("ok");

        ResponseEntity<JsonNode> dependencies =
                rest.getForEntity("/health/dependencies", JsonNode.class);
        assertThat(dependencies.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dependencies.getBody().get("dependencies").fieldNames())
                .toIterable().contains("db", "redis");
    }
}
