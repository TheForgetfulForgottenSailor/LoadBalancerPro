package com.richmond423.loadbalancerpro.api.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class AuthModeConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "loadbalancerpro.auth", name = "mode", havingValue = "oauth2")
    AuthModeValidator authModeValidator(AuthProperties authProperties) {
        return new AuthModeValidator(authProperties);
    }

    static final class AuthModeValidator {
        AuthModeValidator(AuthProperties authProperties) {
            authProperties.validateOAuth2Mode();
        }
    }
}
