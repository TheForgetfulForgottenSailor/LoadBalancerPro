package api;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.profiles.active=cloud-sandbox",
        "loadbalancerpro.api.key=TEST_SANDBOX_API_KEY"
})
@AutoConfigureMockMvc
class CloudSandboxProfileConfigurationTest {
    private static final String API_KEY = "TEST_SANDBOX_API_KEY";
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

    @Autowired
    private Environment environment;

    @Autowired
    private AllocatorService allocatorService;

    @Test
    void cloudSandboxProfileLoadsWithDryRunSafetyDefaults() {
        assertEquals("false", environment.getProperty("cloud.liveMode"));
        assertEquals("false", environment.getProperty("cloud.allowLiveMutation"));
        assertEquals("false", environment.getProperty("cloud.allowResourceDeletion"));
        assertEquals("false", environment.getProperty("cloud.confirmResourceOwnership"));
        assertEquals("false", environment.getProperty("cloud.allowAutonomousScaleUp"));
        assertEquals("2", environment.getProperty("cloud.maxDesiredCapacity"));
        assertEquals("1", environment.getProperty("cloud.maxScaleStep"));
        assertEquals("sandbox", environment.getProperty("cloud.environment"));
        assertEquals("lbp-sandbox-", environment.getProperty("cloud.resourceNamePrefix"));
        assertFalse(allocatorService.isLaseShadowEnabledForTesting());
    }

    @Test
    void cloudSandboxProfileKeepsMetricsExportDisabledByDefault() {
        assertEquals("false", environment.getProperty("management.prometheus.metrics.export.enabled"));
        assertEquals("false", environment.getProperty("management.otlp.metrics.export.enabled"));
        assertEquals("cloud-sandbox", environment.getProperty("management.metrics.tags.environment"));
    }

    @Test
    void cloudSandboxProfileDoesNotExposeMetricsOrPrometheusByDefault() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isNotFound());
    }

    @Test
    void cloudSandboxProfileStartsWithoutAwsCredentialsAndKeepsHealthPublic() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ok")));
    }

    @Test
    void cloudSandboxProfileRejectsMutationWithoutApiKey() throws Exception {
        mockMvc.perform(allocationRequest())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("unauthorized")));
    }

    @Test
    void cloudSandboxProfileAllowsMutationWithCorrectApiKey() throws Exception {
        mockMvc.perform(allocationRequest().header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allocations.api-1").isNumber())
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void cloudSandboxProfileProtectsRoutingCompare() throws Exception {
        mockMvc.perform(routingCompareRequest())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.path", is("/api/routing/compare")));

        mockMvc.perform(routingCompareRequest().header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].strategyId", is("TAIL_LATENCY_POWER_OF_TWO")));
    }

    @Test
    void cloudSandboxProfileProtectsLaseShadowObservability() throws Exception {
        mockMvc.perform(get("/api/lase/shadow"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));

        mockMvc.perform(get("/api/lase/shadow").header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalEvaluations").isNumber());
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
