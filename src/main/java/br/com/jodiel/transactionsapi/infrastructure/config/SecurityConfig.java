package br.com.jodiel.transactionsapi.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import br.com.jodiel.transactionsapi.domain.interfaces.AuthService;
import br.com.jodiel.transactionsapi.domain.interfaces.TokenBlacklistService;
import br.com.jodiel.transactionsapi.presentation.filters.JwtAuthFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/auth/**", "/health/**", "/actuator/**",
            "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**"
    };

    private final ObjectMapper objectMapper;

    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    public SecurityConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, AuthService authService,
                                           TokenBlacklistService tokenBlacklistService) throws Exception {
        // Constructed rather than injected: a JwtAuthFilter bean would also be auto-registered as a
        // plain servlet filter by Boot, running it twice per request.
        JwtAuthFilter jwtAuthFilter = new JwtAuthFilter(authService, tokenBlacklistService, objectMapper);

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(unauthorizedEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler())
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /** Without this, a request with no token would get Spring Security's default 403 instead of 401. */
    @Bean
    public AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, ex) -> writeJson(response, 401, "Authentication required");
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) -> writeJson(response, 403, "Access denied");
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private void writeJson(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), Map.of("message", message));
    }
}
