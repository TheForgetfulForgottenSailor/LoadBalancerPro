package com.richmond423.loadbalancerpro.api.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.StreamUtils;

class RequestSizeLimitFilterTest {
    private static final String API_MUTATION_PATH = "/api/allocate/capacity-aware";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void oversizedRequestWithoutContentLengthReturns413AndDoesNotReachChain() throws Exception {
        RequestSizeLimitFilter filter = new RequestSizeLimitFilter(objectMapper, 8);
        NoContentLengthRequest request = requestWithoutContentLength("123456789");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainReached = new AtomicBoolean(false);

        filter.doFilter(request, response, (ServletRequest servletRequest, ServletResponse servletResponse) ->
                chainReached.set(true));

        assertEquals(413, response.getStatus());
        assertFalse(chainReached.get(), "Oversized no-Content-Length requests must not reach downstream handlers!");
        Map<String, Object> body = objectMapper.readValue(response.getContentAsString(),
                new TypeReference<>() {});
        assertEquals(413, body.get("status"));
        assertEquals("payload_too_large", body.get("error"));
        assertEquals(API_MUTATION_PATH, body.get("path"));
    }

    @Test
    void underLimitRequestWithoutContentLengthReachesChainWithBodyIntact() throws Exception {
        RequestSizeLimitFilter filter = new RequestSizeLimitFilter(objectMapper, 32);
        String json = "{\"ok\":true}";
        NoContentLengthRequest request = requestWithoutContentLength(json);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainReached = new AtomicBoolean(false);
        AtomicReference<String> downstreamBody = new AtomicReference<>();

        filter.doFilter(request, response, (ServletRequest servletRequest, ServletResponse servletResponse) -> {
            chainReached.set(true);
            downstreamBody.set(StreamUtils.copyToString(servletRequest.getInputStream(), StandardCharsets.UTF_8));
        });

        assertEquals(200, response.getStatus());
        assertTrue(chainReached.get(), "Under-limit no-Content-Length requests should continue downstream!");
        assertEquals(json, downstreamBody.get(), "Request body must remain readable after size validation!");
    }

    private static NoContentLengthRequest requestWithoutContentLength(String body) {
        NoContentLengthRequest request = new NoContentLengthRequest();
        request.setMethod("POST");
        request.setRequestURI(API_MUTATION_PATH);
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    private static final class NoContentLengthRequest extends MockHttpServletRequest {
        @Override
        public int getContentLength() {
            return -1;
        }

        @Override
        public long getContentLengthLong() {
            return -1;
        }
    }
}
