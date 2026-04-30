package api;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import core.CloudManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AllocatorControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthReturnsStatusAndVersion() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ok")))
                .andExpect(jsonPath("$.version", is("1.0.0")));
    }

    @Test
    void capacityAwareAllocationReturnsUnallocatedLoadAndRecommendation() throws Exception {
        mockMvc.perform(post("/api/allocate/capacity-aware")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestedLoad": 75.0,
                                  "servers": [
                                    {
                                      "id": "api-1",
                                      "cpuUsage": 90.0,
                                      "memoryUsage": 90.0,
                                      "diskUsage": 90.0,
                                      "capacity": 100.0,
                                      "weight": 1.0,
                                      "healthy": true
                                    },
                                    {
                                      "id": "worker-1",
                                      "cpuUsage": 80.0,
                                      "memoryUsage": 80.0,
                                      "diskUsage": 80.0,
                                      "capacity": 100.0,
                                      "weight": 1.0,
                                      "healthy": true
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allocations.api-1", closeTo(10.0, 0.01)))
                .andExpect(jsonPath("$.allocations.worker-1", closeTo(20.0, 0.01)))
                .andExpect(jsonPath("$.unallocatedLoad", closeTo(45.0, 0.01)))
                .andExpect(jsonPath("$.recommendedAdditionalServers", is(1)))
                .andExpect(jsonPath("$.scalingSimulation.recommendedAdditionalServers", is(1)))
                .andExpect(jsonPath("$.scalingSimulation.simulatedOnly", is(true)))
                .andExpect(jsonPath("$.scalingSimulation.reason", containsString("simulated scale-up")));
    }

    @Test
    void capacityAwareSuccessResponseHasStableBrowserContractShape() throws Exception {
        mockMvc.perform(post("/api/allocate/capacity-aware")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
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
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.allocations").isMap())
                .andExpect(jsonPath("$.unallocatedLoad").isNumber())
                .andExpect(jsonPath("$.recommendedAdditionalServers").isNumber())
                .andExpect(jsonPath("$.scalingSimulation").isMap())
                .andExpect(jsonPath("$.scalingSimulation.simulatedOnly", is(true)))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void predictiveAllocationReturnsUnallocatedLoadAndRecommendation() throws Exception {
        mockMvc.perform(post("/api/allocate/predictive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestedLoad": 20.0,
                                  "servers": [
                                    {
                                      "id": "api-1",
                                      "cpuUsage": 90.0,
                                      "memoryUsage": 90.0,
                                      "diskUsage": 90.0,
                                      "capacity": 100.0,
                                      "weight": 1.0,
                                      "healthy": true
                                    },
                                    {
                                      "id": "worker-1",
                                      "cpuUsage": 80.0,
                                      "memoryUsage": 80.0,
                                      "diskUsage": 80.0,
                                      "capacity": 100.0,
                                      "weight": 1.0,
                                      "healthy": true
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allocations.api-1", closeTo(1.0, 0.01)))
                .andExpect(jsonPath("$.allocations.worker-1", closeTo(12.0, 0.01)))
                .andExpect(jsonPath("$.unallocatedLoad", closeTo(7.0, 0.01)))
                .andExpect(jsonPath("$.recommendedAdditionalServers", is(1)))
                .andExpect(jsonPath("$.scalingSimulation.recommendedAdditionalServers", is(1)))
                .andExpect(jsonPath("$.scalingSimulation.simulatedOnly", is(true)));
    }

    @Test
    void capacityAwareAllocationWithNoUnallocatedLoadRecommendsNoScaleUp() throws Exception {
        mockMvc.perform(post("/api/allocate/capacity-aware")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestedLoad": 25.0,
                                  "servers": [
                                    {
                                      "id": "api-1",
                                      "cpuUsage": 10.0,
                                      "memoryUsage": 10.0,
                                      "diskUsage": 10.0,
                                      "capacity": 100.0,
                                      "weight": 1.0,
                                      "healthy": true
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unallocatedLoad", closeTo(0.0, 0.01)))
                .andExpect(jsonPath("$.recommendedAdditionalServers", is(0)))
                .andExpect(jsonPath("$.scalingSimulation.recommendedAdditionalServers", is(0)))
                .andExpect(jsonPath("$.scalingSimulation.simulatedOnly", is(true)))
                .andExpect(jsonPath("$.scalingSimulation.reason", containsString("No unallocated load")));
    }

    @Test
    void scalingSimulationDoesNotConstructCloudManager() throws Exception {
        try (MockedConstruction<CloudManager> mockedCloudManager =
                Mockito.mockConstruction(CloudManager.class)) {
            mockMvc.perform(post("/api/allocate/capacity-aware")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "requestedLoad": 75.0,
                                      "servers": [
                                        {
                                          "id": "api-1",
                                          "cpuUsage": 90.0,
                                          "memoryUsage": 90.0,
                                          "diskUsage": 90.0,
                                          "capacity": 100.0,
                                          "weight": 1.0,
                                          "healthy": true
                                        }
                                      ]
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.unallocatedLoad", closeTo(65.0, 0.01)))
                    .andExpect(jsonPath("$.scalingSimulation.simulatedOnly", is(true)));

            assertTrue(mockedCloudManager.constructed().isEmpty(),
                    "Scaling simulation must not construct CloudManager or call AWS paths.");
        }
    }

    @Test
    void allocationRejectsInvalidRequest() throws Exception {
        mockMvc.perform(post("/api/allocate/capacity-aware")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestedLoad": -1.0,
                                  "servers": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("validation_failed")))
                .andExpect(jsonPath("$.message", is("Request validation failed")))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.path", is("/api/allocate/capacity-aware")))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    @Test
    void allocationRejectsInvalidServerInput() throws Exception {
        mockMvc.perform(post("/api/allocate/predictive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestedLoad": 10.0,
                                  "servers": [
                                    {
                                      "id": "",
                                      "cpuUsage": 150.0,
                                      "memoryUsage": 20.0,
                                      "diskUsage": 20.0,
                                      "capacity": 100.0,
                                      "weight": 1.0,
                                      "healthy": true
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("validation_failed")))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void malformedJsonReturnsConsistentSafeErrorShape() throws Exception {
        mockMvc.perform(post("/api/allocate/capacity-aware")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestedLoad": 10.0,
                                  "servers": [
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("bad_request")))
                .andExpect(jsonPath("$.message", is("Malformed JSON request body")))
                .andExpect(jsonPath("$.path", is("/api/allocate/capacity-aware")))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    @Test
    void oversizedJsonRequestIsRejectedWithSafeErrorShape() throws Exception {
        String oversizedId = "S".repeat(20_000);
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
                """.formatted(oversizedId);

        mockMvc.perform(post("/api/allocate/capacity-aware")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oversizedBody))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status", is(413)))
                .andExpect(jsonPath("$.error", is("payload_too_large")))
                .andExpect(jsonPath("$.message", containsString("Request body exceeds")))
                .andExpect(jsonPath("$.path", is("/api/allocate/capacity-aware")))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    @Test
    void corsPreflightAllowsConfiguredBrowserOrigins() throws Exception {
        mockMvc.perform(options("/api/allocate/capacity-aware")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("POST")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("Content-Type")));
    }

    @Test
    void corsPreflightDeniesUnconfiguredOrigins() throws Exception {
        mockMvc.perform(options("/api/allocate/capacity-aware")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void apiResponsesIncludeExpectedSecurityHeadersWithoutLocalHsts() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
    }

    @Test
    void actuatorHealthInfoAndMetricsAreAvailable() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists());

        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app.name", is("LoadBalancerPro")));

        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names").isArray());

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("jvm_info")));
    }

    @Test
    void actuatorDoesNotExposeEnvironmentEndpointByDefault() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isNotFound());
    }

    @Test
    void actuatorReadinessIsAvailable() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists());
    }

    @Test
    void swaggerUiAndOpenApiDocsAreAvailable() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.paths./api/allocate/capacity-aware").exists());
    }
}
