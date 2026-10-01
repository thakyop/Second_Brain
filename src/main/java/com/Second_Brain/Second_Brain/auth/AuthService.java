
package com.Second_Brain.Second_Brain.auth;

import com.Second_Brain.Second_Brain.User.User;
import com.Second_Brain.Second_Brain.User.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final long expirationSeconds;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtEncoder jwtEncoder,
            @Value("${app.jwt.expiration-seconds}") long expirationSeconds) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.expirationSeconds = expirationSeconds;
    }

    @Transactional
    public AuthResponse register(AuthRequests.RegisterRequest request) {
        String email = normalizeEmail(request.email());

        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Email is already registered");
        }

        User user = userRepository.save(
                new User(email, passwordEncoder.encode(request.password())));

        return createToken(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(AuthRequests.LoginRequest request) {
        String email = normalizeEmail(request.email());

        User user = userRepository.findByEmail(email)
                .orElseThrow(AuthService::invalidCredentials);

        if (!passwordEncoder.matches(
                request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }

        return createToken(user);
    }

    private AuthResponse createToken(User user) {
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("second-brain")
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(expirationSeconds))
                .claim("email", user.getEmail())
                .build();

        String token = jwtEncoder.encode(
            JwtEncoderParameters.from(
                org.springframework.security.oauth2.jwt.JwsHeader
                    .with(MacAlgorithm.HS256).build(),
                claims)).getTokenValue();

        return new AuthResponse(
                token, "Bearer", expirationSeconds,
                user.getId(), user.getEmail());
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }
}