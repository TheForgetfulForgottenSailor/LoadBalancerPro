package api.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicBoolean;

import api.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Profile("prod")
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class ProdApiKeyFilter extends OncePerRequestFilter {
    private static final Logger logger = LoggerFactory.getLogger(ProdApiKeyFilter.class);
    private static final String API_KEY_HEADER = "X-API-Key";

    private final ObjectMapper objectMapper;
    private final byte[] configuredApiKey;
    private final boolean apiKeyConfigured;
    private final AtomicBoolean missingKeyWarningLogged = new AtomicBoolean(false);

    public ProdApiKeyFilter(ObjectMapper objectMapper,
                            @Value("${loadbalancerpro.api.key:}") String configuredApiKey) {
        this.objectMapper = objectMapper;
        String normalizedKey = configuredApiKey == null ? "" : configuredApiKey.trim();
        this.apiKeyConfigured = !normalizedKey.isEmpty();
        this.configuredApiKey = normalizedKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!isProtectedApiRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!apiKeyConfigured) {
            logMissingKeyWarningOnce();
            writeUnauthorized(request, response);
            return;
        }

        String presentedApiKey = request.getHeader(API_KEY_HEADER);
        if (presentedApiKey == null || presentedApiKey.isBlank()
                || !constantTimeEquals(configuredApiKey, presentedApiKey.getBytes(StandardCharsets.UTF_8))) {
            writeUnauthorized(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static boolean isProtectedApiRequest(HttpServletRequest request) {
        return isProtectedApiMutation(request) || isProtectedLaseObservability(request);
    }

    private static boolean isProtectedApiMutation(HttpServletRequest request) {
        String method = request.getMethod();
        return request.getRequestURI().startsWith("/api/")
                && ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method));
    }

    private static boolean isProtectedLaseObservability(HttpServletRequest request) {
        return "GET".equals(request.getMethod()) && request.getRequestURI().startsWith("/api/lase/");
    }

    private static boolean constantTimeEquals(byte[] expected, byte[] actual) {
        return MessageDigest.isEqual(sha256(expected), sha256(actual));
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest algorithm is unavailable", exception);
        }
    }

    private void logMissingKeyWarningOnce() {
        if (missingKeyWarningLogged.compareAndSet(false, true)) {
            logger.warn("Prod profile API key is not configured; protected API requests will be rejected.");
        }
    }

    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiErrorResponse.unauthorized(request.getRequestURI()));
    }
}
