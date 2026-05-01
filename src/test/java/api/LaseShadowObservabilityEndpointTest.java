package api;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import core.CloudManager;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "loadbalancerpro.lase.shadow.enabled=true")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class LaseShadowObservabilityEndpointTest {
    private static final String REQUEST_BODY = """
            {
              "requestedLoad": 60.0,
              "servers": [
                {
                  "id": "api-1",
                  "cpuUsage": 20.0,
                  "memoryUsage": 20.0,
                  "diskUsage": 20.0,
                  "capacity": 80.0,
                  "weight": 1.0,
                  "healthy": true
                },
                {
                  "id": "worker-1",
                  "cpuUsage": 10.0,
                  "memoryUsage": 10.0,
                  "diskUsage": 10.0,
                  "capacity": 100.0,
                  "weight": 1.0,
                  "healthy": true
                }
              ]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shadowObservabilityEndpointStartsWithEmptyBoundedSummary() throws Exception {
        mockMvc.perform(get("/api/lase/shadow"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.maxSize", is(100)))
                .andExpect(jsonPath("$.summary.totalEvaluations", is(0)))
                .andExpect(jsonPath("$.summary.agreementCount", is(0)))
                .andExpect(jsonPath("$.summary.comparableEvaluations", is(0)))
                .andExpect(jsonPath("$.summary.agreementRate", closeTo(0.0, 0.001)))
                .andExpect(jsonPath("$.summary.failSafeCount", is(0)))
                .andExpect(jsonPath("$.summary.recommendationCounts").isMap())
                .andExpect(jsonPath("$.recentEvents").isArray())
                .andExpect(jsonPath("$.recentEvents").isEmpty());
    }

    @Test
    void allocationRecordsShadowEventVisibleThroughEndpointWithoutCloudCalls() throws Exception {
        try (MockedConstruction<CloudManager> mockedCloudManager = Mockito.mockConstruction(CloudManager.class)) {
            mockMvc.perform(post("/api/allocate/capacity-aware")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.allocations").isMap());

            mockMvc.perform(get("/api/lase/shadow"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.summary.totalEvaluations", is(1)))
                    .andExpect(jsonPath("$.summary.failSafeCount", is(0)))
                    .andExpect(jsonPath("$.summary.latestEventTimestamp").isString())
                    .andExpect(jsonPath("$.summary.recommendationCounts").isMap())
                    .andExpect(jsonPath("$.recentEvents[0].evaluationId", is("lase-shadow-capacity-aware")))
                    .andExpect(jsonPath("$.recentEvents[0].strategy", is("CAPACITY_AWARE")))
                    .andExpect(jsonPath("$.recentEvents[0].requestedLoad", closeTo(60.0, 0.001)))
                    .andExpect(jsonPath("$.recentEvents[0].actualSelectedServerId", is("worker-1")))
                    .andExpect(jsonPath("$.recentEvents[0].recommendedAction").isString())
                    .andExpect(jsonPath("$.recentEvents[0].reason", containsString("Evaluation lase-shadow-capacity-aware")))
                    .andExpect(jsonPath("$.recentEvents[0].failSafe", is(false)));

            assertTrue(mockedCloudManager.constructed().isEmpty(),
                    "Shadow observability must not construct CloudManager or call cloud paths.");
        }
    }

    @Test
    void shadowObservabilityDoesNotChangeAllocationResponse() throws Exception {
        mockMvc.perform(post("/api/allocate/capacity-aware")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allocations.api-1", closeTo(24.0, 0.01)))
                .andExpect(jsonPath("$.allocations.worker-1", closeTo(36.0, 0.01)))
                .andExpect(jsonPath("$.unallocatedLoad", closeTo(0.0, 0.01)))
                .andExpect(jsonPath("$.recommendedAdditionalServers", is(0)));
    }
}
