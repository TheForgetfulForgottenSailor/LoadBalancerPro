package api.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@EnableConfigurationProperties(TelemetryProperties.class)
public class TelemetryConfiguration {
    @Bean
    static BeanFactoryPostProcessor telemetryOtlpEndpointValidator(Environment environment) {
        return beanFactory -> TelemetryStartupGuard.validateOtlpMetricsEndpoint(
                environment,
                Binder.get(environment)
                        .bind("loadbalancerpro.telemetry", TelemetryProperties.class)
                        .orElseGet(TelemetryProperties::new));
    }

    @Bean
    TelemetryStartupGuard telemetryStartupGuard(Environment environment, TelemetryProperties telemetryProperties) {
        return new TelemetryStartupGuard(environment, telemetryProperties);
    }
}
