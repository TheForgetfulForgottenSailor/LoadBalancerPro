package com.richmond423.loadbalancerpro.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "loadbalancerpro.auth")
public class AuthProperties {
    private Mode mode = Mode.API_KEY;
    private boolean docsPublic = false;
    private OAuth2 oauth2 = new OAuth2();
    private RequiredRole requiredRole = new RequiredRole();

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode == null ? Mode.API_KEY : mode;
    }

    public boolean isDocsPublic() {
        return docsPublic;
    }

    public void setDocsPublic(boolean docsPublic) {
        this.docsPublic = docsPublic;
    }

    public OAuth2 getOauth2() {
        return oauth2;
    }

    public void setOauth2(OAuth2 oauth2) {
        this.oauth2 = oauth2 == null ? new OAuth2() : oauth2;
    }

    public RequiredRole getRequiredRole() {
        return requiredRole;
    }

    public void setRequiredRole(RequiredRole requiredRole) {
        this.requiredRole = requiredRole == null ? new RequiredRole() : requiredRole;
    }

    public boolean isOAuth2Mode() {
        return mode == Mode.OAUTH2;
    }

    public void validateOAuth2Mode() {
        if (!isOAuth2Mode()) {
            return;
        }
        if (!StringUtils.hasText(oauth2.issuerUri) && !StringUtils.hasText(oauth2.jwkSetUri)) {
            throw new IllegalStateException("OAuth2 auth mode requires loadbalancerpro.auth.oauth2.issuer-uri "
                    + "or loadbalancerpro.auth.oauth2.jwk-set-uri");
        }
        normalizedLaseShadowRole();
        normalizedAllocationRole();
    }

    public String normalizedLaseShadowRole() {
        return normalizeRequiredRole(requiredRole.laseShadow, "loadbalancerpro.auth.required-role.lase-shadow");
    }

    public String normalizedAllocationRole() {
        return normalizeRequiredRole(requiredRole.allocation, "loadbalancerpro.auth.required-role.allocation");
    }

    private static String normalizeRequiredRole(String role, String propertyName) {
        if (!StringUtils.hasText(role)) {
            throw new IllegalStateException(propertyName + " must not be blank when OAuth2 auth mode is active");
        }
        String trimmed = role.trim();
        return trimmed.startsWith("ROLE_") ? trimmed.substring("ROLE_".length()) : trimmed;
    }

    public enum Mode {
        API_KEY,
        OAUTH2
    }

    public static final class OAuth2 {
        private String issuerUri = "";
        private String jwkSetUri = "";

        public String getIssuerUri() {
            return issuerUri;
        }

        public void setIssuerUri(String issuerUri) {
            this.issuerUri = issuerUri;
        }

        public String getJwkSetUri() {
            return jwkSetUri;
        }

        public void setJwkSetUri(String jwkSetUri) {
            this.jwkSetUri = jwkSetUri;
        }
    }

    public static final class RequiredRole {
        private String laseShadow = "observer";
        private String allocation = "operator";

        public String getLaseShadow() {
            return laseShadow;
        }

        public void setLaseShadow(String laseShadow) {
            this.laseShadow = laseShadow;
        }

        public String getAllocation() {
            return allocation;
        }

        public void setAllocation(String allocation) {
            this.allocation = allocation;
        }
    }
}
