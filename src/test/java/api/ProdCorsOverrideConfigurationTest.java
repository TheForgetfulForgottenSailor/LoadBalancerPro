package api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.profiles.active=prod",
        "loadbalancerpro.api.cors.allowed-origins=https://app.example.com"
})
@AutoConfigureMockMvc
class ProdCorsOverrideConfigurationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void prodProfileAllowsExplicitlyConfiguredCorsOrigin() throws Exception {
        mockMvc.perform(options("/api/allocate/capacity-aware")
                        .header("Origin", "https://app.example.com")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://app.example.com"))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("POST")));
    }

    @Test
    void prodProfileAllowsApiKeyHeaderInCorsPreflightForConfiguredOrigin() throws Exception {
        mockMvc.perform(options("/api/allocate/capacity-aware")
                        .header("Origin", "https://app.example.com")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type,X-API-Key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://app.example.com"))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("Content-Type")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("X-API-Key")));
    }

    @Test
    void prodProfileAllowsProtectedMutationMethodsInCorsPreflightForConfiguredOrigin() throws Exception {
        mockMvc.perform(options("/api/allocate/capacity-aware")
                        .header("Origin", "https://app.example.com")
                        .header("Access-Control-Request-Method", "PUT")
                        .header("Access-Control-Request-Headers", "Content-Type,Authorization,X-API-Key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://app.example.com"))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("PUT")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("Authorization")));

        mockMvc.perform(options("/api/allocate/capacity-aware")
                        .header("Origin", "https://app.example.com")
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "Content-Type,Authorization,X-API-Key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://app.example.com"))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("PATCH")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("Authorization")));
    }
}
