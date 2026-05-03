package api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

@SpringBootTest
class OpenTelemetryMetricsConfigurationTest {
    @Autowired
    private Environment environment;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void otlpExportIsDisabledByDefaultWithStableResourceAttributes() {
        assertEquals("false", environment.getProperty("management.otlp.metrics.export.enabled"));
        assertEquals("loadbalancerpro", environment.getProperty("management.metrics.tags.application"));
        assertEquals("local", environment.getProperty("management.metrics.tags.environment"));
        assertEquals("true", environment.getProperty("loadbalancerpro.telemetry.otlp.require-private-endpoint"));
        assertEquals("true", environment.getProperty("loadbalancerpro.telemetry.otlp.allow-insecure-localhost"));
        assertEquals("true", environment.getProperty("loadbalancerpro.telemetry.startup-summary.enabled"));

        Map<String, String> resourceAttributes = resourceAttributes(environment);
        assertEquals("loadbalancerpro", resourceAttributes.get("service.name"));
        assertEquals("1.1.1", resourceAttributes.get("service.version"));
        assertEquals("local", resourceAttributes.get("deployment.environment"));
        assertFalse(OtlpRegistryAssertions.hasOtlpRegistry(meterRegistry));
    }

    private static Map<String, String> resourceAttributes(Environment environment) {
        return Binder.get(environment)
                .bind("management.opentelemetry.resource-attributes",
                        Bindable.mapOf(String.class, String.class))
                .orElseThrow(() -> new IllegalStateException("OpenTelemetry resource attributes are not bound"));
    }
}

@SpringBootTest(properties = {
        "management.otlp.metrics.export.enabled=true",
        "management.otlp.metrics.export.step=1h",
        "management.otlp.metrics.export.url=http://localhost:4318/v1/metrics"
})
class OpenTelemetryMetricsEnabledConfigurationTest {
    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void optInOtlpMetricsExportCreatesOtlpRegistry() {
        assertTrue(OtlpRegistryAssertions.hasOtlpRegistry(meterRegistry));
    }
}

@SpringBootTest(properties = {
        "management.otlp.metrics.export.enabled=false",
        "management.otlp.metrics.export.url=http://127.0.0.1:1/v1/metrics"
})
class OpenTelemetryMetricsDisabledCollectorConfigurationTest {
    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void disabledOtlpMetricsDoNotRequireCollectorReachability() {
        assertFalse(OtlpRegistryAssertions.hasOtlpRegistry(meterRegistry));
    }
}

@SpringBootTest(properties = {
        "spring.profiles.active=prod",
        "LOADBALANCERPRO_OTLP_METRICS_ENABLED=true",
        "OTEL_EXPORTER_OTLP_METRICS_ENDPOINT=http://localhost:4318/v1/metrics",
        "management.otlp.metrics.export.step=1h"
})
class OpenTelemetryMetricsEnvironmentOptInConfigurationTest {
    @Autowired
    private Environment environment;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void environmentOptInCreatesOtlpRegistry() {
        assertEquals("true", environment.getProperty("management.otlp.metrics.export.enabled"));
        assertEquals("http://localhost:4318/v1/metrics",
                environment.getProperty("management.otlp.metrics.export.url"));
        assertTrue(OtlpRegistryAssertions.hasOtlpRegistry(meterRegistry));
    }
}

final class OtlpRegistryAssertions {
    private OtlpRegistryAssertions() {
    }

    static boolean hasOtlpRegistry(MeterRegistry registry) {
        if (registry instanceof OtlpMeterRegistry) {
            return true;
        }
        if (registry instanceof CompositeMeterRegistry composite) {
            return composite.getRegistries().stream().anyMatch(OtlpRegistryAssertions::hasOtlpRegistry);
        }
        return false;
    }
}
