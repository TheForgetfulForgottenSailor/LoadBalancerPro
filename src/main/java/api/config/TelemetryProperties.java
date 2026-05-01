package api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "loadbalancerpro.telemetry")
public class TelemetryProperties {
    private final Otlp otlp = new Otlp();
    private final StartupSummary startupSummary = new StartupSummary();

    public Otlp getOtlp() {
        return otlp;
    }

    public StartupSummary getStartupSummary() {
        return startupSummary;
    }

    public static class Otlp {
        private boolean requirePrivateEndpoint = true;
        private boolean allowInsecureLocalhost = true;

        public boolean isRequirePrivateEndpoint() {
            return requirePrivateEndpoint;
        }

        public void setRequirePrivateEndpoint(boolean requirePrivateEndpoint) {
            this.requirePrivateEndpoint = requirePrivateEndpoint;
        }

        public boolean isAllowInsecureLocalhost() {
            return allowInsecureLocalhost;
        }

        public void setAllowInsecureLocalhost(boolean allowInsecureLocalhost) {
            this.allowInsecureLocalhost = allowInsecureLocalhost;
        }
    }

    public static class StartupSummary {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
