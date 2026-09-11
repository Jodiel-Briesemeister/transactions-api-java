package br.com.jodiel.transactionsapi.presentation.filters;

import com.fasterxml.jackson.databind.ObjectMapper;
import br.com.jodiel.transactionsapi.domain.errors.AppException;
import br.com.jodiel.transactionsapi.domain.interfaces.AuthService;
import br.com.jodiel.transactionsapi.domain.interfaces.TokenBlacklistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Reads the Bearer token, rejects revoked ones and puts the user id in the security context as the
 * authentication principal, which is what {@code @AuthenticationPrincipal String userId} resolves.
 *
 * <p>Deliberately not a Spring bean: Boot auto-registers every {@code Filter} bean in the servlet
 * container, which would run this filter a second time outside the security chain. It is
 * constructed by hand in SecurityConfig instead.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;
    private final TokenBlacklistService tokenBlacklistService;
    private final ObjectMapper objectMapper;

    public JwtAuthFilter(AuthService authService, TokenBlacklistService tokenBlacklistService,
                         ObjectMapper objectMapper) {
        this.authService = authService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();

        try {
            if (tokenBlacklistService.has(token)) {
                log.warn("Revoked token used");
                throw new AppException("Token revoked", 401);
            }

            String userId = authService.verifyAccessToken(token);

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(userId, null, List.of()));

        } catch (AppException e) {
            SecurityContextHolder.clearContext();
            writeError(response, e);
            return;
        }

        chain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, AppException e) throws IOException {
        response.setStatus(e.getStatusCode());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), Map.of("message", e.getMessage()));
    }
}
