package api;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.profiles.active=prod")
@AutoConfigureMockMvc
class ProdProfileConfigurationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Environment environment;

    @Autowired
    private AllocatorService allocatorService;

    @Test
    void prodProfileLoadsWithoutLiveCloudEnabled() {
        assertEquals("false", environment.getProperty("cloud.liveMode"));
    }

    @Test
    void prodProfileKeepsMetricsExportDisabledByDefault() {
        assertEquals("false", environment.getProperty("management.prometheus.metrics.export.enabled"));
        assertEquals("false", environment.getProperty("management.otlp.metrics.export.enabled"));
        assertEquals("prod", environment.getProperty("management.metrics.tags.environment"));
    }

    @Test
    void prodProfileKeepsLaseShadowAdvisorDisabledByDefault() {
        assertFalse(allocatorService.isLaseShadowEnabledForTesting());
    }

    @Test
    void prodProfileKeepsHealthAndInfoAvailable() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists());

        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app.name", is("LoadBalancerPro")));
    }

    @Test
    void prodProfileDoesNotExposeMetricsOrPrometheusByDefault() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isNotFound());
    }

    @Test
    void prodProfileDoesNotAllowDefaultLocalhostCorsOriginsUnlessConfigured() throws Exception {
        mockMvc.perform(options("/api/allocate/capacity-aware")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
