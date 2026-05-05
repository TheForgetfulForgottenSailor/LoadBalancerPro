package com.richmond423.loadbalancerpro.api;

import com.richmond423.loadbalancerpro.core.CloudManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "loadbalancerpro.lase.shadow.enabled=true")
@AutoConfigureMockMvc
class LaseShadowAdvisorConfigurationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AllocatorService allocatorService;

    @Test
    void defaultEnvironmentDisablesShadowAdvisor() {
        assertFalse(AllocatorService.resolveLaseShadowEnabled(new MockEnvironment()));
    }

    @Test
    void canonicalSpringPropertyEnablesShadowAdvisor() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("loadbalancerpro.lase.shadow.enabled", "true");

        assertTrue(AllocatorService.resolveLaseShadowEnabled(environment));
    }

    @Test
    void environmentStylePropertyEnablesShadowAdvisor() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("LOADBALANCERPRO_LASE_SHADOW_ENABLED", "true");

        assertTrue(AllocatorService.resolveLaseShadowEnabled(environment));
    }

    @Test
    void springPropertyEnablesApiCreatedLoadBalancersWithoutChangingAllocationOrCloudSafety() throws Exception {
        assertTrue(allocatorService.isLaseShadowEnabledForTesting());

        try (MockedConstruction<CloudManager> mockedCloudManager = Mockito.mockConstruction(CloudManager.class)) {
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
                    .andExpect(jsonPath("$.scalingSimulation.simulatedOnly", is(true)));

            assertTrue(mockedCloudManager.constructed().isEmpty(),
                    "Enabled shadow advisor must not construct CloudManager or call cloud paths.");
        }
    }
}
