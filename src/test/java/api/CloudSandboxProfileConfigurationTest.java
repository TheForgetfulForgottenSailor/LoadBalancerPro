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
        assertFalse(allocatorService.isLaseShadowEnabledForTesting());
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
}
