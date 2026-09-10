package br.com.jodiel.transactionsapi.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import br.com.jodiel.transactionsapi.domain.enums.TransactionType;
import br.com.jodiel.transactionsapi.presentation.filters.RateLimitFilter;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Registered by hand, ahead of the Spring Security chain, so that a flood of requests is
     * rejected before any authentication work is done. A plain {@code @Component} filter would be
     * appended after the security chain instead.
     */
    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(
            LettuceBasedProxyManager<String> proxyManager,
            ObjectMapper objectMapper,
            @Value("${app.rate-limit.global-tries}") long globalTries,
            @Value("${app.rate-limit.auth-tries}") long authTries,
            @Value("${app.rate-limit.login-tries}") long loginTries,
            @Value("${app.rate-limit.window-minutes}") long windowMinutes) {

        RateLimitFilter filter = new RateLimitFilter(
                proxyManager, objectMapper, globalTries, authTries, loginTries, windowMinutes);

        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/*");
        registration.setOrder(SecurityProperties.DEFAULT_FILTER_ORDER - 10);
        return registration;
    }

    /**
     * Lets {@code GET /transactions?type=deposit} work. Spring's default enum binding matches the
     * constant name (DEPOSIT), but the wire format of this API is the lowercase value, as in the
     * Node version.
     */
    @Override
    public void addFormatters(@NonNull FormatterRegistry registry) {
        registry.addConverter(String.class, TransactionType.class, new TransactionTypeConverter());
    }

    static class TransactionTypeConverter implements Converter<String, TransactionType> {
        @Override
        public TransactionType convert(@NonNull String source) {
            if (source.isBlank()) return null;
            for (TransactionType type : TransactionType.values()) {
                if (type.getValue().equalsIgnoreCase(source) || type.name().equalsIgnoreCase(source)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown transaction type: " + source);
        }
    }
}
