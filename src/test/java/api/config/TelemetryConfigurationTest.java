package api.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class TelemetryConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TelemetryConfiguration.class)
            .withPropertyValues(
                    "loadbalancerpro.telemetry.startup-summary.enabled=false",
                    "management.prometheus.metrics.export.enabled=false",
                    "management.endpoints.web.exposure.include=health,info");

    @Test
    void otlpDisabledAllowsStartupWithNoEndpoint() {
        contextRunner.withPropertyValues(
                        "management.otlp.metrics.export.enabled=false",
                        "management.otlp.metrics.export.url=")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void otlpEnabledWithNoEndpointFailsStartup() {
        contextRunner.withPropertyValues(
                        "management.otlp.metrics.export.enabled=true",
                        "management.otlp.metrics.export.url=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCause(context.getStartupFailure()))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("management.otlp.metrics.export.url")
                            .hasMessageContaining("missing or blank");
                });
    }

    @Test
    void otlpEnabledWithMalformedEndpointFailsStartup() {
        contextRunner.withPropertyValues(
                        "management.otlp.metrics.export.enabled=true",
                        "management.otlp.metrics.export.url=http://[bad/v1/metrics")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCause(context.getStartupFailure()))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("valid URI");
                });
    }

    @Test
    void otlpEnabledWithLocalhostEndpointPassesWhenLocalhostAllowed() {
        contextRunner.withPropertyValues(
                        "management.otlp.metrics.export.enabled=true",
                        "management.otlp.metrics.export.url=http://localhost:4318/v1/metrics")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void otlpEnabledWithLocalhostEndpointFailsWhenLocalhostDisallowed() {
        contextRunner.withPropertyValues(
                        "management.otlp.metrics.export.enabled=true",
                        "management.otlp.metrics.export.url=http://127.0.0.1:4318/v1/metrics",
                        "loadbalancerpro.telemetry.otlp.allow-insecure-localhost=false")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCause(context.getStartupFailure()))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("allow-insecure-localhost=false");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://10.8.0.12:4318/v1/metrics",
            "http://172.16.0.12:4318/v1/metrics",
            "http://172.31.255.12:4318/v1/metrics",
            "http://192.168.10.12:4318/v1/metrics"
    })
    void otlpEnabledWithPrivateIpEndpointPasses(String endpoint) {
        contextRunner.withPropertyValues(
                        "management.otlp.metrics.export.enabled=true",
                        "management.otlp.metrics.export.url=" + endpoint)
                .run(context -> assertThat(context).hasNotFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://collector.local/v1/metrics",
            "https://collector.internal/v1/metrics",
            "https://collector.lan/v1/metrics"
    })
    void otlpEnabledWithInternalHostnamePasses(String endpoint) {
        contextRunner.withPropertyValues(
                        "management.otlp.metrics.export.enabled=true",
                        "management.otlp.metrics.export.url=" + endpoint)
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void otlpEnabledWithPublicEndpointFailsWhenPrivateEndpointRequired() {
        contextRunner.withPropertyValues(
                        "management.otlp.metrics.export.enabled=true",
                        "management.otlp.metrics.export.url=https://otel.example.com/v1/metrics")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCause(context.getStartupFailure()))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("not private")
                            .hasMessageContaining("require-private-endpoint=false");
                });
    }

    @Test
    void otlpEnabledWithPublicEndpointPassesWhenPrivateRequirementIsExplicitlyDisabled() {
        contextRunner.withPropertyValues(
                        "management.otlp.metrics.export.enabled=true",
                        "management.otlp.metrics.export.url=https://otel.example.com/v1/metrics",
                        "loadbalancerpro.telemetry.otlp.require-private-endpoint=false")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void otlpEnabledWithCredentialOrQueryEndpointFailsStartup() {
        contextRunner.withPropertyValues(
                        "management.otlp.metrics.export.enabled=true",
                        "management.otlp.metrics.export.url=https://user:secret@collector.internal/v1/metrics?token=abc")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCause(context.getStartupFailure()))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("must not include credentials")
                            .hasMessageContaining("query strings");
                });
    }

    @Test
    void otlpEnabledWithFragmentEndpointFailsStartup() {
        contextRunner.withPropertyValues(
                        "management.otlp.metrics.export.enabled=true",
                        "management.otlp.metrics.export.url=https://collector.internal/v1/metrics#token=abc")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCause(context.getStartupFailure()))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("fragments");
                });
    }

    @Test
    void startupSummarySanitizesEndpointSecretsAndRequestDetails() {
        String summary = TelemetryStartupGuard.buildStartupSummary(
                true,
                false,
                "health,info",
                "https://user:super-secret@collector.internal:4318/v1/metrics?token=abc123&Authorization=Bearer%20abc");

        assertThat(summary)
                .contains("telemetry.otlp.metrics.enabled=true")
                .contains("prometheus.export.enabled=false")
                .contains("actuator.exposure=health,info")
                .contains("otlp.endpoint.host=collector.internal")
                .doesNotContain("super-secret")
                .doesNotContain("token")
                .doesNotContain("abc123")
                .doesNotContain("Authorization")
                .doesNotContain("Bearer")
                .doesNotContain("/v1/metrics")
                .doesNotContain("?");
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }
}
