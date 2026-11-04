package com.Second_Brain.Second_Brain.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * Spring renders an error with a second, internal request to /error (an ERROR
 * dispatch). MockMvc never makes that request, so these tests start a real
 * server. They use the Docker dev database and write nothing to it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityErrorResponsesTest {

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        // A random secret for this test run, so no real JWT_SECRET is needed.
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        String encoded = Base64.getEncoder().encodeToString(secret);
        registry.add("app.jwt.secret", () -> encoded);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private JwtEncoder jwtEncoder;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void wrongMethodOnPublicEndpointKeepsItsStatus() throws Exception {
        HttpResponse<String> response = send(request("/api/auth/register").GET().build());

        assertThat(response.statusCode()).isEqualTo(405);
    }

    @Test
    void invalidRegistrationKeepsItsStatus() throws Exception {
        HttpResponse<String> response = send(request("/api/auth/register")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"email": "not-an-email", "password": "short"}
                        """))
                .build());

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void protectedEndpointStillNeedsAToken() throws Exception {
        HttpResponse<String> response = send(request("/api/workspaces").GET().build());

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void errorPageCannotBeRequestedDirectlyWithoutAToken() throws Exception {
        // Only the internal error forward is public, not the /error URL itself.
        HttpResponse<String> response = send(request("/error").GET().build());

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void errorOnProtectedEndpointKeepsItsStatus() throws Exception {
        HttpResponse<String> response = send(request("/api/documents/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + tokenFor(UUID.randomUUID()))
                .GET()
                .build());

        assertThat(response.statusCode()).isEqualTo(404);
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    // Signed with the application's own key, like a token from AuthService.
    private String tokenFor(UUID userId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("second-brain")
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
