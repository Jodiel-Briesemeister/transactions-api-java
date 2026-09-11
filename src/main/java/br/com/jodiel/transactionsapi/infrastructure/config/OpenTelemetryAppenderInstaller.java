package br.com.jodiel.transactionsapi.infrastructure.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * Logback creates the OTEL appender from logback-spring.xml before the Spring context exists, so the
 * SDK cannot be injected into it. Once Spring Boot has built the SDK, with its OTLP log exporter, this
 * hands it over; events logged before that point are buffered by the appender and sent afterward.
 */
@Component
public class OpenTelemetryAppenderInstaller implements InitializingBean {

    private final OpenTelemetry openTelemetry;

    public OpenTelemetryAppenderInstaller(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    @Override
    public void afterPropertiesSet() {
        OpenTelemetryAppender.install(openTelemetry);
    }
}
