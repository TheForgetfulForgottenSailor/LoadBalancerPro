package api;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
        "loadbalancerpro.auth.mode=API_KEY",
        "loadbalancerpro.api.key=TEST_PROD_API_KEY"
})
@AutoConfigureMockMvc
class ProdApiKeyModeAliasProtectionTest {
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

    @Autowired
    private MockMvc mockMvc;

    @Test
    void prodProfileKeepsApiKeyProtectionWhenModeUsesEnumStyleSpelling() throws Exception {
        mockMvc.perform(allocationRequest())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("unauthorized")));

        mockMvc.perform(allocationRequest().header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allocations.api-1").isNumber())
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder allocationRequest() {
        return post("/api/allocate/capacity-aware")
                .contentType(MediaType.APPLICATION_JSON)
                .content(REQUEST_BODY);
    }
}
