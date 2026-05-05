package com.richmond423.loadbalancerpro.api.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;

final class TelemetryStartupGuard implements InitializingBean {
    private static final Logger logger = LoggerFactory.getLogger(TelemetryStartupGuard.class);
    private static final String OTLP_METRICS_ENABLED = "management.otlp.metrics.export.enabled";
    private static final String OTLP_METRICS_URL = "management.otlp.metrics.export.url";
    private static final String PROMETHEUS_EXPORT_ENABLED = "management.prometheus.metrics.export.enabled";
    private static final String ACTUATOR_EXPOSURE = "management.endpoints.web.exposure.include";

    private final Environment environment;
    private final TelemetryProperties telemetryProperties;

    TelemetryStartupGuard(Environment environment, TelemetryProperties telemetryProperties) {
        this.environment = environment;
        this.telemetryProperties = telemetryProperties;
    }

    @Override
    public void afterPropertiesSet() {
        boolean otlpMetricsEnabled = Boolean.parseBoolean(environment.getProperty(OTLP_METRICS_ENABLED, "false"));
        String endpoint = environment.getProperty(OTLP_METRICS_URL, "");
        if (telemetryProperties.getStartupSummary().isEnabled()) {
            logger.info(buildStartupSummary(
                    otlpMetricsEnabled,
                    Boolean.parseBoolean(environment.getProperty(PROMETHEUS_EXPORT_ENABLED, "false")),
                    environment.getProperty(ACTUATOR_EXPOSURE, ""),
                    endpoint));
        }
    }

    static void validateOtlpMetricsEndpoint(Environment environment, TelemetryProperties telemetryProperties) {
        boolean otlpMetricsEnabled = Boolean.parseBoolean(environment.getProperty(OTLP_METRICS_ENABLED, "false"));
        String endpoint = environment.getProperty(OTLP_METRICS_URL, "");
        validateOtlpMetricsEndpoint(otlpMetricsEnabled, endpoint, telemetryProperties);
    }

    private static void validateOtlpMetricsEndpoint(
            boolean otlpMetricsEnabled, String endpoint, TelemetryProperties telemetryProperties) {
        if (!otlpMetricsEnabled) {
            return;
        }
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalStateException(
                    "OTLP metrics export is enabled but management.otlp.metrics.export.url is missing or blank");
        }

        URI uri = endpointUri(endpoint);
        if (uri == null) {
            throw new IllegalStateException("OTLP metrics endpoint must be a valid URI");
        }
        if (uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalStateException(
                    "OTLP metrics endpoint must not include credentials, query strings, or fragments");
        }
        String host = endpointHost(uri);
        if (host.isBlank()) {
            throw new IllegalStateException("OTLP metrics endpoint must include a host");
        }
        if (isLocalhost(host) && !telemetryProperties.getOtlp().isAllowInsecureLocalhost()) {
            throw new IllegalStateException(
                    "OTLP metrics endpoint uses localhost but loadbalancerpro.telemetry.otlp.allow-insecure-localhost=false");
        }
        if (telemetryProperties.getOtlp().isRequirePrivateEndpoint() && !isPrivateEndpointHost(host)) {
            throw new IllegalStateException(
                    "OTLP metrics endpoint host is not private; set loadbalancerpro.telemetry.otlp.require-private-endpoint=false only for an explicitly trusted collector");
        }
    }

    static String buildStartupSummary(
            boolean otlpMetricsEnabled, boolean prometheusExportEnabled, String actuatorExposure, String endpoint) {
        return "telemetry.otlp.metrics.enabled=%s prometheus.export.enabled=%s actuator.exposure=%s otlp.endpoint.host=%s"
                .formatted(
                        otlpMetricsEnabled,
                        prometheusExportEnabled,
                        blankToNone(actuatorExposure),
                        sanitizedEndpointHost(endpoint));
    }

    static String sanitizedEndpointHost(String endpoint) {
        String host = endpointHost(endpoint);
        return host.isBlank() ? "<none>" : host;
    }

    private static String endpointHost(String endpoint) {
        return endpointHost(endpointUri(endpoint));
    }

    private static String endpointHost(URI uri) {
        if (uri == null) {
            return "";
        }
        String host = uri.getHost();
        return host == null ? "" : host.toLowerCase(Locale.ROOT);
    }

    private static URI endpointUri(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return null;
        }
        try {
            return new URI(endpoint.trim());
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private static boolean isPrivateEndpointHost(String host) {
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        return isLocalhost(normalizedHost)
                || isPrivateIpv4(normalizedHost)
                || normalizedHost.endsWith(".local")
                || normalizedHost.endsWith(".internal")
                || normalizedHost.endsWith(".lan");
    }

    private static boolean isLocalhost(String host) {
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        return "localhost".equals(normalizedHost) || "127.0.0.1".equals(normalizedHost);
    }

    private static boolean isPrivateIpv4(String host) {
        String[] parts = host.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        int[] octets = new int[4];
        for (int i = 0; i < parts.length; i++) {
            try {
                octets[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                return false;
            }
            if (octets[i] < 0 || octets[i] > 255 || !Integer.toString(octets[i]).equals(parts[i])) {
                return false;
            }
        }
        return octets[0] == 10
                || (octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31)
                || (octets[0] == 192 && octets[1] == 168);
    }

    private static String blankToNone(String value) {
        return value == null || value.isBlank() ? "<none>" : value.trim();
    }
}
