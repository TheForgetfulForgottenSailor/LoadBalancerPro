package com.richmond423.loadbalancerpro.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.richmond423.loadbalancerpro.core.CloudManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class RoutingControllerTest {
    private static final String VALID_REQUEST = """
            {
              "strategies": ["TAIL_LATENCY_POWER_OF_TWO"],
              "servers": [
                {
                  "serverId": "green",
                  "healthy": true,
                  "inFlightRequestCount": 5,
                  "configuredCapacity": 100.0,
                  "estimatedConcurrencyLimit": 100.0,
                  "averageLatencyMillis": 20.0,
                  "p95LatencyMillis": 40.0,
                  "p99LatencyMillis": 80.0,
                  "recentErrorRate": 0.01,
                  "queueDepth": 1,
                  "networkAwareness": {
                    "timeoutRate": 0.0,
                    "retryRate": 0.0,
                    "connectionFailureRate": 0.0,
                    "latencyJitterMillis": 4.0,
                    "recentErrorBurst": false,
                    "requestTimeoutCount": 0,
                    "sampleSize": 120
                  }
                },
                {
                  "serverId": "blue",
                  "healthy": true,
                  "inFlightRequestCount": 75,
                  "configuredCapacity": 100.0,
                  "estimatedConcurrencyLimit": 100.0,
                  "averageLatencyMillis": 35.0,
                  "p95LatencyMillis": 120.0,
                  "p99LatencyMillis": 220.0,
                  "recentErrorRate": 0.15,
                  "queueDepth": 10
                }
              ]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void validLocalRequestReturnsTailLatencyPowerOfTwoResultWithoutCloudMutationPath() throws Exception {
        try (MockedConstruction<CloudManager> mockedCloudManager =
                Mockito.mockConstruction(CloudManager.class)) {
            mockMvc.perform(routingCompare(VALID_REQUEST))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.requestedStrategies[0]", is("TAIL_LATENCY_POWER_OF_TWO")))
                    .andExpect(jsonPath("$.candidateCount", is(2)))
                    .andExpect(jsonPath("$.results[0].strategyId", is("TAIL_LATENCY_POWER_OF_TWO")))
                    .andExpect(jsonPath("$.results[0].status", is("SUCCESS")))
                    .andExpect(jsonPath("$.results[0].chosenServerId", is("green")))
                    .andExpect(jsonPath("$.results[0].reason", containsString("Chose green")))
                    .andExpect(jsonPath("$.results[0].candidateServersConsidered[0]", is("green")))
                    .andExpect(jsonPath("$.results[0].candidateServersConsidered[1]", is("blue")))
                    .andExpect(jsonPath("$.results[0].scores.green").isNumber())
                    .andExpect(jsonPath("$.results[0].scores.blue").isNumber())
                    .andExpect(jsonPath("$.error").doesNotExist());

            assertTrue(mockedCloudManager.constructed().isEmpty(),
                    "Routing comparison must not construct CloudManager or call AWS paths.");
        }
    }

    @Test
    void missingStrategiesDefaultsToRegisteredRoutingStrategiesInOrder() throws Exception {
        mockMvc.perform(routingCompare("""
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
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedStrategies[0]", is("TAIL_LATENCY_POWER_OF_TWO")))
                .andExpect(jsonPath("$.requestedStrategies[1]", is("WEIGHTED_LEAST_LOAD")))
                .andExpect(jsonPath("$.requestedStrategies[2]", is("ROUND_ROBIN")))
                .andExpect(jsonPath("$.results[0].strategyId", is("TAIL_LATENCY_POWER_OF_TWO")))
                .andExpect(jsonPath("$.results[0].chosenServerId", is("green")))
                .andExpect(jsonPath("$.results[1].strategyId", is("WEIGHTED_LEAST_LOAD")))
                .andExpect(jsonPath("$.results[1].chosenServerId", is("green")))
                .andExpect(jsonPath("$.results[2].strategyId", is("ROUND_ROBIN")))
                .andExpect(jsonPath("$.results[2].chosenServerId", is("green")));
    }

    @Test
    void explicitWeightedLeastLoadRequestUsesRoutingWeightWithoutCloudMutationPath() throws Exception {
        try (MockedConstruction<CloudManager> mockedCloudManager =
                     Mockito.mockConstruction(CloudManager.class)) {
            mockMvc.perform(routingCompare("""
                            {
                              "strategies": ["WEIGHTED_LEAST_LOAD"],
                              "servers": [
                                {
                                  "serverId": "base",
                                  "healthy": true,
                                  "inFlightRequestCount": 20,
                                  "configuredCapacity": 100.0,
                                  "estimatedConcurrencyLimit": 100.0,
                                  "weight": 1.0,
                                  "averageLatencyMillis": 10.0,
                                  "p95LatencyMillis": 20.0,
                                  "p99LatencyMillis": 40.0,
                                  "recentErrorRate": 0.0,
                                  "queueDepth": 0
                                },
                                {
                                  "serverId": "weighted",
                                  "healthy": true,
                                  "inFlightRequestCount": 20,
                                  "configuredCapacity": 100.0,
                                  "estimatedConcurrencyLimit": 100.0,
                                  "weight": 4.0,
                                  "averageLatencyMillis": 10.0,
                                  "p95LatencyMillis": 20.0,
                                  "p99LatencyMillis": 40.0,
                                  "recentErrorRate": 0.0,
                                  "queueDepth": 0
                                }
                              ]
                            }
                            """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.requestedStrategies[0]", is("WEIGHTED_LEAST_LOAD")))
                    .andExpect(jsonPath("$.candidateCount", is(2)))
                    .andExpect(jsonPath("$.results[0].strategyId", is("WEIGHTED_LEAST_LOAD")))
                    .andExpect(jsonPath("$.results[0].status", is("SUCCESS")))
                    .andExpect(jsonPath("$.results[0].chosenServerId", is("weighted")))
                    .andExpect(jsonPath("$.results[0].candidateServersConsidered[0]", is("base")))
                    .andExpect(jsonPath("$.results[0].candidateServersConsidered[1]", is("weighted")))
                    .andExpect(jsonPath("$.results[0].scores.base").isNumber())
                    .andExpect(jsonPath("$.results[0].scores.weighted").isNumber())
                    .andExpect(jsonPath("$.error").doesNotExist());

            assertTrue(mockedCloudManager.constructed().isEmpty(),
                    "Weighted routing comparison must not construct CloudManager or call AWS paths.");
        }
    }

    @Test
    void explicitRoundRobinRequestUsesRequestOrderWithoutCloudMutationPath() throws Exception {
        try (MockedConstruction<CloudManager> mockedCloudManager =
                     Mockito.mockConstruction(CloudManager.class)) {
            mockMvc.perform(routingCompare("""
                            {
                              "strategies": ["ROUND_ROBIN"],
                              "servers": [
                                {
                                  "serverId": "green",
                                  "healthy": true,
                                  "inFlightRequestCount": 20,
                                  "averageLatencyMillis": 10.0,
                                  "p95LatencyMillis": 20.0,
                                  "p99LatencyMillis": 40.0,
                                  "recentErrorRate": 0.0
                                },
                                {
                                  "serverId": "blue",
                                  "healthy": true,
                                  "inFlightRequestCount": 5,
                                  "averageLatencyMillis": 10.0,
                                  "p95LatencyMillis": 20.0,
                                  "p99LatencyMillis": 40.0,
                                  "recentErrorRate": 0.0
                                }
                              ]
                            }
                            """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.requestedStrategies[0]", is("ROUND_ROBIN")))
                    .andExpect(jsonPath("$.candidateCount", is(2)))
                    .andExpect(jsonPath("$.results[0].strategyId", is("ROUND_ROBIN")))
                    .andExpect(jsonPath("$.results[0].status", is("SUCCESS")))
                    .andExpect(jsonPath("$.results[0].chosenServerId", is("green")))
                    .andExpect(jsonPath("$.results[0].candidateServersConsidered[0]", is("green")))
                    .andExpect(jsonPath("$.results[0].candidateServersConsidered[1]", is("blue")))
                    .andExpect(jsonPath("$.results[0].scores").isEmpty())
                    .andExpect(jsonPath("$.results[0].reason", containsString("round-robin position 1 of 2")))
                    .andExpect(jsonPath("$.error").doesNotExist());

            assertTrue(mockedCloudManager.constructed().isEmpty(),
                    "Round-robin routing comparison must not construct CloudManager or call AWS paths.");
        }
    }

    @Test
    void emptyServersReturnsStructuredBadRequest() throws Exception {
        mockMvc.perform(routingCompare("""
                        {
                          "strategies": ["TAIL_LATENCY_POWER_OF_TWO"],
                          "servers": []
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("validation_failed")))
                .andExpect(jsonPath("$.path", is("/api/routing/compare")))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void duplicateServerIdsReturnStructuredBadRequest() throws Exception {
        mockMvc.perform(routingCompare("""
                        {
                          "strategies": ["TAIL_LATENCY_POWER_OF_TWO"],
                          "servers": [
                            {
                              "serverId": "green",
                              "healthy": true,
                              "inFlightRequestCount": 1,
                              "averageLatencyMillis": 10.0,
                              "p95LatencyMillis": 20.0,
                              "p99LatencyMillis": 30.0,
                              "recentErrorRate": 0.0
                            },
                            {
                              "serverId": "green",
                              "healthy": true,
                              "inFlightRequestCount": 2,
                              "averageLatencyMillis": 11.0,
                              "p95LatencyMillis": 21.0,
                              "p99LatencyMillis": 31.0,
                              "recentErrorRate": 0.0
                            }
                          ]
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("bad_request")))
                .andExpect(jsonPath("$.message", containsString("serverId must be unique")))
                .andExpect(jsonPath("$.path", is("/api/routing/compare")));
    }

    @Test
    void unknownStrategyIdReturnsStructuredBadRequest() throws Exception {
        mockMvc.perform(routingCompare("""
                        {
                          "strategies": ["NOT_A_REAL_STRATEGY"],
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
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("bad_request")))
                .andExpect(jsonPath("$.message", containsString("Unsupported routing strategy")))
                .andExpect(jsonPath("$.path", is("/api/routing/compare")));
    }

    @Test
    void invalidRoutingWeightReturnsStructuredBadRequest() throws Exception {
        mockMvc.perform(routingCompare("""
                        {
                          "strategies": ["WEIGHTED_LEAST_LOAD"],
                          "servers": [
                            {
                              "serverId": "green",
                              "healthy": true,
                              "inFlightRequestCount": 1,
                              "weight": -1.0,
                              "averageLatencyMillis": 10.0,
                              "p95LatencyMillis": 20.0,
                              "p99LatencyMillis": 30.0,
                              "recentErrorRate": 0.0
                            }
                          ]
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("bad_request")))
                .andExpect(jsonPath("$.message", containsString("weight")))
                .andExpect(jsonPath("$.path", is("/api/routing/compare")));
    }

    @Test
    void duplicateStrategyIdsReturnStructuredBadRequest() throws Exception {
        mockMvc.perform(routingCompare("""
                        {
                          "strategies": ["TAIL_LATENCY_POWER_OF_TWO", "tail-latency-power-of-two"],
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
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("bad_request")))
                .andExpect(jsonPath("$.message", containsString("duplicate")));
    }

    @Test
    void invalidMetricRangesReturnStructuredBadRequest() throws Exception {
        mockMvc.perform(routingCompare("""
                        {
                          "strategies": ["TAIL_LATENCY_POWER_OF_TWO"],
                          "servers": [
                            {
                              "serverId": "green",
                              "healthy": true,
                              "inFlightRequestCount": 1,
                              "averageLatencyMillis": 30.0,
                              "p95LatencyMillis": 20.0,
                              "p99LatencyMillis": 40.0,
                              "recentErrorRate": 0.0
                            }
                          ]
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("bad_request")))
                .andExpect(jsonPath("$.message", containsString("averageLatencyMillis")));
    }

    @Test
    void allUnhealthyCandidatesReturnSafeNoDecisionResult() throws Exception {
        mockMvc.perform(routingCompare("""
                        {
                          "strategies": ["TAIL_LATENCY_POWER_OF_TWO"],
                          "servers": [
                            {
                              "serverId": "green",
                              "healthy": false,
                              "inFlightRequestCount": 1,
                              "averageLatencyMillis": 10.0,
                              "p95LatencyMillis": 20.0,
                              "p99LatencyMillis": 30.0,
                              "recentErrorRate": 0.0
                            }
                          ]
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].strategyId", is("TAIL_LATENCY_POWER_OF_TWO")))
                .andExpect(jsonPath("$.results[0].status", is("SUCCESS")))
                .andExpect(jsonPath("$.results[0].chosenServerId", nullValue()))
                .andExpect(jsonPath("$.results[0].candidateServersConsidered").isEmpty())
                .andExpect(jsonPath("$.results[0].reason", containsString("No healthy eligible servers")));
    }

    @Test
    void unsupportedMediaTypeReturnsStructuredError() throws Exception {
        mockMvc.perform(post("/api/routing/compare")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(VALID_REQUEST))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status", is(415)))
                .andExpect(jsonPath("$.error", is("unsupported_media_type")))
                .andExpect(jsonPath("$.path", is("/api/routing/compare")));
    }

    @Test
    void wrongHttpMethodReturnsStructuredError() throws Exception {
        mockMvc.perform(put("/api/routing/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status", is(405)))
                .andExpect(jsonPath("$.error", is("method_not_allowed")))
                .andExpect(jsonPath("$.path", is("/api/routing/compare")));
    }

    private static MockHttpServletRequestBuilder routingCompare(String requestBody) {
        return post("/api/routing/compare")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody);
    }
}
