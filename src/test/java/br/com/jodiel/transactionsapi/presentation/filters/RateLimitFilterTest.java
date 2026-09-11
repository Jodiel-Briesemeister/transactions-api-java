package br.com.jodiel.transactionsapi.presentation.filters;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    private static final String CLIENT_ADDRESS = "203.0.113.7";

    @Mock private LettuceBasedProxyManager<String> proxyManager;
    @Mock private RemoteBucketBuilder<String> bucketBuilder;
    @Mock private BucketProxy bucket;

    private RateLimitFilter sut;

    @BeforeEach
    void setUp() {
        sut = new RateLimitFilter(proxyManager, new ObjectMapper(), 100, 50, 10, 15);
        when(proxyManager.builder()).thenReturn(bucketBuilder);
        when(bucketBuilder.build(anyString(), Mockito.<Supplier<BucketConfiguration>>any()))
                .thenReturn(bucket);
    }

    private MockHttpServletRequest loginRequest(String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setRemoteAddr(CLIENT_ADDRESS);
        request.addHeader("X-Forwarded-For", forwardedFor);
        return request;
    }

    @Test
    @DisplayName("keys the bucket by the connection address, ignoring a client-supplied X-Forwarded-For")
    void ignoresForwardedForHeader() throws Exception {
        when(bucket.tryConsume(1)).thenReturn(true);

        sut.doFilter(loginRequest("198.51.100.1"), new MockHttpServletResponse(), new MockFilterChain());
        sut.doFilter(loginRequest("198.51.100.2"), new MockHttpServletResponse(), new MockFilterChain());

        verify(bucketBuilder, times(2))
                .build(eq("rl:login:" + CLIENT_ADDRESS), Mockito.<Supplier<BucketConfiguration>>any());
    }

    @Test
    @DisplayName("answers 429 with Retry-After once the budget is spent")
    void rejectsWhenBudgetIsSpent() throws Exception {
        when(bucket.tryConsume(1)).thenReturn(false);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        sut.doFilter(loginRequest("198.51.100.1"), response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("900");
        assertThat(chain.getRequest()).as("request never reached the rest of the chain").isNull();
    }
}
