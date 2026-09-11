package br.com.jodiel.transactionsapi.infrastructure.services;

import br.com.jodiel.transactionsapi.domain.errors.AppException;
import br.com.jodiel.transactionsapi.domain.interfaces.AuthService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

@Service
public class JwtService implements AuthService {

    private final SecretKey key;
    private final long accessTokenExpiresInSeconds;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.access-token-expires-in-seconds}") long accessTokenExpiresInSeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiresInSeconds = accessTokenExpiresInSeconds;
    }

    @Override
    public String generateAccessToken(String userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTokenExpiresInSeconds)))
                .signWith(key)
                .compact();
    }

    @Override
    public String verifyAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            throw new AppException("Invalid or expired token", 401);
        }
    }

    @Override
    public long getTokenRemainingSeconds(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            long exp = claims.getExpiration().getTime() / 1000;
            long now = Instant.now().getEpochSecond();
            return Math.max(0, exp - now);
        } catch (JwtException | IllegalArgumentException e) {
            return 0;
        }
    }
}
