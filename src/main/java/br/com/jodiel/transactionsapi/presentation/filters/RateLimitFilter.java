package br.com.jodiel.transactionsapi.presentation.filters;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Redis-backed rate limiting with three budgets: a tight one on credential endpoints, a looser one
 * on the rest of /auth, and a global one for everything else.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final LettuceBasedProxyManager<String> proxyManager;
    private final ObjectMapper objectMapper;
    private final long globalTries;
    private final long authTries;
    private final long loginTries;
    private final Duration window;

    public RateLimitFilter(LettuceBasedProxyManager<String> proxyManager, ObjectMapper objectMapper,
                           long globalTries, long authTries, long loginTries, long windowMinutes) {
        this.proxyManager = proxyManager;
        this.objectMapper = objectMapper;
        this.globalTries = globalTries;
        this.authTries = authTries;
        this.loginTries = loginTries;
        this.window = Duration.ofMinutes(windowMinutes);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator")
                || path.startsWith("/health")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        Scope scope = resolveScope(request.getRequestURI());
        String key = "rl:" + scope.name() + ":" + clientIp(request);

        if (!tryConsume(key, scope.limit())) {
            log.warn("Rate limit exceeded scope={} path={} ip={}",
                    scope.name(), request.getRequestURI(), clientIp(request));

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setHeader("Retry-After", String.valueOf(window.toSeconds()));
            objectMapper.writeValue(response.getWriter(),
                    Map.of("message", "Too many requests, please try again later"));
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean tryConsume(String key, long limit) {
        BucketConfiguration config = BucketConfiguration.builder()
                .addLimit(Bandwidth.builder().capacity(limit).refillGreedy(limit, window).build())
                .build();
        try {
            Bucket bucket = proxyManager.builder().build(key, () -> config);
            return bucket.tryConsume(1);
        } catch (RuntimeException e) {
            // Fail open: a Redis outage should degrade rate limiting, not take the API offline.
            log.error("Rate limiter unavailable, allowing request key={}", key, e);
            return true;
        }
    }

    /**
     * The budget is part of the key: /auth/login and /auth/register have different limits, and a
     * shared key would let whichever endpoint is hit first define the budget for both.
     */
    private Scope resolveScope(String path) {
        if (path.startsWith("/auth/login") || path.startsWith("/auth/reactivate")) {
            return new Scope("login", loginTries);
        }
        if (path.startsWith("/auth")) {
            return new Scope("auth", authTries);
        }
        return new Scope("global", globalTries);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) return request.getRemoteAddr();
        return forwarded.split(",")[0].trim();
    }

    private record Scope(String name, long limit) {}
}
