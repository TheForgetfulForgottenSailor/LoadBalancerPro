package api.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import api.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
public class ApiSecurityConfiguration {
    private static final String VIEWER_ROLE = "viewer";

    @Bean
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http,
                                               AuthProperties authProperties,
                                               ObjectMapper objectMapper,
                                               Converter<Jwt, ? extends AbstractAuthenticationToken>
                                                       loadBalancerProJwtAuthenticationConverter)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);

        if (!authProperties.isOAuth2Mode()) {
            http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
            return http.build();
        }

        authProperties.validateOAuth2Mode();
        String laseShadowRole = authProperties.normalizedLaseShadowRole();
        String allocationRole = authProperties.normalizedAllocationRole();
        String[] readRoles = distinctRoles(VIEWER_ROLE, laseShadowRole, allocationRole);
        AuthenticationEntryPoint authenticationEntryPoint = (request, response, exception) ->
                writeError(objectMapper, response,
                        ApiErrorResponse.unauthorized(request.getRequestURI(),
                                "Valid Bearer token required for this endpoint"));
        AccessDeniedHandler accessDeniedHandler = (request, response, exception) ->
                writeError(objectMapper, response, ApiErrorResponse.forbidden(request.getRequestURI()));

        http.exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll();
                    authorize.requestMatchers(HttpMethod.GET, "/api/health").permitAll();
                    configureDocsAuthorization(authorize, authProperties, readRoles);
                    authorize.requestMatchers(HttpMethod.GET, "/api/lase/shadow")
                            .hasAnyRole(laseShadowRole, allocationRole);
                    authorize.requestMatchers(HttpMethod.POST, "/api/routing/**").hasRole(allocationRole);
                    authorize.requestMatchers(HttpMethod.POST, "/api/allocate/**").hasRole(allocationRole);
                    authorize.requestMatchers(HttpMethod.PUT, "/api/allocate/**").hasRole(allocationRole);
                    authorize.requestMatchers(HttpMethod.PATCH, "/api/allocate/**").hasRole(allocationRole);
                    authorize.requestMatchers("/actuator/health/**", "/actuator/info").hasAnyRole(readRoles);
                    authorize.requestMatchers("/api/**").authenticated();
                    authorize.anyRequest().denyAll();
                })
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(loadBalancerProJwtAuthenticationConverter)));

        return http.build();
    }

    @Bean
    Converter<Jwt, ? extends AbstractAuthenticationToken> loadBalancerProJwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(ApiSecurityConfiguration::extractRoleAuthorities);
        return converter;
    }

    @Bean
    @ConditionalOnProperty(prefix = "loadbalancerpro.auth", name = "mode", havingValue = "oauth2")
    @ConditionalOnMissingBean(JwtDecoder.class)
    JwtDecoder loadBalancerProJwtDecoder(AuthProperties authProperties) {
        authProperties.validateOAuth2Mode();
        String jwkSetUri = authProperties.getOauth2().getJwkSetUri();
        String issuerUri = authProperties.getOauth2().getIssuerUri();
        if (StringUtils.hasText(jwkSetUri)) {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri.trim()).build();
            if (StringUtils.hasText(issuerUri)) {
                decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuerUri.trim()));
            }
            return decoder;
        }
        return JwtDecoders.fromIssuerLocation(issuerUri.trim());
    }

    private static void configureDocsAuthorization(
            org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer<HttpSecurity>
                    .AuthorizationManagerRequestMatcherRegistry authorize,
            AuthProperties authProperties,
            String[] readRoles) {
        if (authProperties.isDocsPublic()) {
            authorize.requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                    .permitAll();
            return;
        }
        authorize.requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                .hasAnyRole(readRoles);
    }

    private static Collection<GrantedAuthority> extractRoleAuthorities(Jwt jwt) {
        Set<String> roles = new LinkedHashSet<>();
        addRoleClaim(jwt.getClaim("roles"), roles);
        addRoleClaim(jwt.getClaim("role"), roles);
        addRoleClaim(jwt.getClaim("authorities"), roles);
        addRoleClaim(jwt.getClaim("scope"), roles);
        addRoleClaim(jwt.getClaim("scp"), roles);
        Object realmAccess = jwt.getClaim("realm_access");
        if (realmAccess instanceof Map<?, ?> realmAccessMap) {
            addRoleClaim(realmAccessMap.get("roles"), roles);
        }
        return roles.stream()
                .map(ApiSecurityConfiguration::roleAuthority)
                .map(GrantedAuthority.class::cast)
                .toList();
    }

    private static void addRoleClaim(Object claim, Set<String> roles) {
        if (claim instanceof String value) {
            Arrays.stream(value.split("[,\\s]+"))
                    .map(String::trim)
                    .filter(role -> !role.isEmpty())
                    .forEach(roles::add);
            return;
        }
        if (claim instanceof Collection<?> values) {
            values.forEach(value -> addRoleClaim(value, roles));
        }
    }

    private static SimpleGrantedAuthority roleAuthority(String role) {
        String trimmed = role.trim();
        if (trimmed.startsWith("ROLE_")) {
            trimmed = trimmed.substring("ROLE_".length());
        }
        if (trimmed.startsWith("SCOPE_")) {
            trimmed = trimmed.substring("SCOPE_".length());
        }
        String authority = "ROLE_" + trimmed;
        return new SimpleGrantedAuthority(authority);
    }

    private static String[] distinctRoles(String... roles) {
        return Arrays.stream(roles)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toArray(String[]::new);
    }

    private static void writeError(ObjectMapper objectMapper,
                                   HttpServletResponse response,
                                   ApiErrorResponse errorResponse) throws IOException {
        response.setStatus(errorResponse.status());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), errorResponse);
    }
}
