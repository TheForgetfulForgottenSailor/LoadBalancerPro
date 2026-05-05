package com.richmond423.loadbalancerpro.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AuthModeConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AuthModeConfiguration.class);

    @Test
    void apiKeyModeDoesNotRequireOAuth2IssuerOrJwkConfiguration() {
        contextRunner.withPropertyValues("loadbalancerpro.auth.mode=api-key")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void oauth2ModeFailsStartupWhenIssuerAndJwkConfigurationAreBlank() {
        contextRunner.withPropertyValues(
                        "loadbalancerpro.auth.mode=oauth2",
                        "loadbalancerpro.auth.oauth2.issuer-uri= ",
                        "loadbalancerpro.auth.oauth2.jwk-set-uri=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    Throwable rootCause = rootCause(context.getStartupFailure());
                    assertThat(rootCause)
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("loadbalancerpro.auth.oauth2.issuer-uri")
                            .hasMessageContaining("loadbalancerpro.auth.oauth2.jwk-set-uri");
                });
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }
}
