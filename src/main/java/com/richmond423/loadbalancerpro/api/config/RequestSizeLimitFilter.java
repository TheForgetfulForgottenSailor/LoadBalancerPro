package com.richmond423.loadbalancerpro.api.config;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import com.richmond423.loadbalancerpro.api.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(SecurityProperties.DEFAULT_FILTER_ORDER + 1)
public class RequestSizeLimitFilter extends OncePerRequestFilter {
    private final ObjectMapper objectMapper;
    private final long maxRequestBytes;

    public RequestSizeLimitFilter(ObjectMapper objectMapper,
                                  @Value("${loadbalancerpro.api.max-request-bytes:16384}") long maxRequestBytes) {
        this.objectMapper = objectMapper;
        this.maxRequestBytes = maxRequestBytes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long contentLength = request.getContentLengthLong();
        if (isApiMutation(request) && contentLength > maxRequestBytes) {
            writePayloadTooLarge(request, response);
            return;
        }
        if (isApiMutation(request)) {
            BodyReadResult body = readBodyWithinLimit(request);
            if (body.oversized()) {
                writePayloadTooLarge(request, response);
                return;
            }
            filterChain.doFilter(new CachedBodyRequest(request, body.bytes()), response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean isApiMutation(HttpServletRequest request) {
        String method = request.getMethod();
        return request.getRequestURI().startsWith("/api/")
                && ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method));
    }

    private BodyReadResult readBodyWithinLimit(HttpServletRequest request) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream((int) Math.min(maxRequestBytes, 8192));
        byte[] buffer = new byte[4096];
        long totalRead = 0;
        try (ServletInputStream input = request.getInputStream()) {
            while (true) {
                long remainingBeforeLimit = maxRequestBytes + 1 - totalRead;
                if (remainingBeforeLimit <= 0) {
                    return BodyReadResult.tooLarge();
                }
                int bytesToRead = (int) Math.min(buffer.length, remainingBeforeLimit);
                int read = input.read(buffer, 0, bytesToRead);
                if (read == -1) {
                    return BodyReadResult.withinLimit(output.toByteArray());
                }
                totalRead += read;
                if (totalRead > maxRequestBytes) {
                    return BodyReadResult.tooLarge();
                }
                output.write(buffer, 0, read);
            }
        }
    }

    private void writePayloadTooLarge(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(),
                ApiErrorResponse.payloadTooLarge(request.getRequestURI(), maxRequestBytes));
    }

    private record BodyReadResult(byte[] bytes, boolean oversized) {
        private static BodyReadResult withinLimit(byte[] bytes) {
            return new BodyReadResult(bytes, false);
        }

        private static BodyReadResult tooLarge() {
            return new BodyReadResult(new byte[0], true);
        }
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body.clone();
        }

        @Override
        public ServletInputStream getInputStream() {
            return new CachedBodyServletInputStream(body);
        }

        @Override
        public BufferedReader getReader() {
            Charset charset = getCharacterEncoding() != null
                    ? Charset.forName(getCharacterEncoding())
                    : StandardCharsets.UTF_8;
            return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(body), charset));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }

    private static final class CachedBodyServletInputStream extends ServletInputStream {
        private final ByteArrayInputStream input;

        private CachedBodyServletInputStream(byte[] body) {
            this.input = new ByteArrayInputStream(body);
        }

        @Override
        public int read() {
            return input.read();
        }

        @Override
        public boolean isFinished() {
            return input.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            // Synchronous request processing only; no async listener is needed.
        }
    }
}
