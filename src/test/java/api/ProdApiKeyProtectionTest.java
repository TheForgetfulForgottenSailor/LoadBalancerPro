package api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.profiles.active=prod",
        "loadbalancerpro.api.key=TEST_PROD_API_KEY"
})
@AutoConfigureMockMvc
class ProdApiKeyProtectionTest {
    private static final String API_KEY = "TEST_PROD_API_KEY";
    private static final String REQUEST_BODY = """
            {
              "requestedLoad": 10.0,
              "servers": [
                {
                  "id": "api-1",
                  "cpuUsage": 10.0,
                  "memoryUsage": 20.0,
                  "diskUsage": 30.0,
                  "capacity": 100.0,
                  "weight": 1.0,
                  "healthy": true
                }
              ]
            }
            """;
    private static final String ROUTING_REQUEST_BODY = """
            {
              "servers": [
                {
                  "serverId": "green",
                  "healthy": true,
                  "inFlightRequestCount": 1,
                  "averageLatencyMillis": 10.0,
                  "p95LatencyMillis": 20.0,
                  "p99LatencyMillis": 30.0,
                  "recentErrorRate": 0.0
                }
              ]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void prodProfileRejectsAllocationRequestWithoutApiKey() throws Exception {
        mockMvc.perform(allocationRequest())
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("unauthorized")))
                .andExpect(jsonPath("$.message", containsString("API key")))
                .andExpect(jsonPath("$.path", is("/api/allocate/capacity-aware")))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    void prodProfileProtectsPredictiveAllocationRequests() throws Exception {
        mockMvc.perform(post("/api/allocate/predictive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.path", is("/api/allocate/predictive")));
    }

    @Test
    void prodProfileRejectsOversizedUnauthenticatedMutationBeforeRequestSizeFilter() throws Exception {
        String oversizedBody = """
                {
                  "requestedLoad": 10.0,
                  "servers": [
                    {
                      "id": "%s",
                      "cpuUsage": 10.0,
                      "memoryUsage": 20.0,
                      "diskUsage": 30.0,
                      "capacity": 100.0,
                      "weight": 1.0,
                      "healthy": true
                    }
                  ]
                }
                """.formatted("S".repeat(20_000));

        mockMvc.perform(post("/api/allocate/capacity-aware")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oversizedBody))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("unauthorized")))
                .andExpect(jsonPath("$.path", is("/api/allocate/capacity-aware")))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    @Test
    void prodProfileRejectsAllocationRequestWithWrongApiKeyWithoutLeakingConfiguredKey() throws Exception {
        mockMvc.perform(allocationRequest().header("X-API-Key", "WRONG_TEST_KEY"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(not(containsString(API_KEY))))
                .andExpect(content().string(not(containsString("WRONG_TEST_KEY"))))
                .andExpect(jsonPath("$.error", is("unauthorized")))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    @Test
    void prodProfileAllowsAllocationRequestWithCorrectApiKey() throws Exception {
        mockMvc.perform(allocationRequest().header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allocations.api-1").isNumber())
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void prodProfileRejectsRoutingCompareWithoutApiKey() throws Exception {
        mockMvc.perform(routingCompareRequest())
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("unauthorized")))
                .andExpect(jsonPath("$.path", is("/api/routing/compare")));
    }

    @Test
    void prodProfileRejectsRoutingCompareWithWrongApiKeyWithoutLeakingConfiguredKey() throws Exception {
        mockMvc.perform(routingCompareRequest().header("X-API-Key", "WRONG_TEST_KEY"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(not(containsString(API_KEY))))
                .andExpect(content().string(not(containsString("WRONG_TEST_KEY"))))
                .andExpect(jsonPath("$.error", is("unauthorized")))
                .andExpect(jsonPath("$.path", is("/api/routing/compare")));
    }

    @Test
    void prodProfileAllowsRoutingCompareWithCorrectApiKey() throws Exception {
        mockMvc.perform(routingCompareRequest().header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedStrategies[0]", is("TAIL_LATENCY_POWER_OF_TWO")))
                .andExpect(jsonPath("$.results[0].status", is("SUCCESS")))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void prodProfileKeepsApiHealthPublic() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ok")));
    }

    @Test
    void prodProfileProtectsLaseShadowObservabilityWithoutApiKey() throws Exception {
        mockMvc.perform(get("/api/lase/shadow"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("unauthorized")))
                .andExpect(jsonPath("$.path", is("/api/lase/shadow")));
    }

    @Test
    void prodProfileAllowsLaseShadowObservabilityWithCorrectApiKey() throws Exception {
        mockMvc.perform(get("/api/lase/shadow").header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalEvaluations").isNumber())
                .andExpect(jsonPath("$.recentEvents").isArray());
    }

    @Test
    void prodProfileKeepsOpenApiDocsPublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.openapi").exists());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder allocationRequest() {
        return post("/api/allocate/capacity-aware")
                .contentType(MediaType.APPLICATION_JSON)
                .content(REQUEST_BODY);
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder routingCompareRequest() {
        return post("/api/routing/compare")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ROUTING_REQUEST_BODY);
    }
}
