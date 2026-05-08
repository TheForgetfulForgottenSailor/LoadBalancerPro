package com.richmond423.loadbalancerpro.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class RoutingOpenApiContractTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void openApiDocsExposeRoutingComparePostOperation() throws Exception {
        JsonNode docs = openApiDocs();
        JsonNode operation = required(docs, "/paths/~1api~1routing~1compare/post");

        JsonNode requestContent = required(operation, "/requestBody/content");
        assertFalse(requestContent.path("application/json").isMissingNode(),
                "routing comparison should declare an application/json request body");
        assertRef(requestContent.at("/application~1json/schema"), "#/components/schemas/RoutingComparisonRequest");

        JsonNode response = required(operation, "/responses/200");
        assertFalse(response.isMissingNode(), "routing comparison should declare a 200 response");
    }

    @Test
    void openApiDocsCharacterizeInferredRoutingCompareSchemas() throws Exception {
        JsonNode docs = openApiDocs();

        JsonNode requestProperties = required(docs,
                "/components/schemas/RoutingComparisonRequest/properties");
        assertEquals("array", required(requestProperties, "/servers/type").asText());
        assertRef(required(requestProperties, "/servers/items"), "#/components/schemas/RoutingServerStateInput");
        assertEquals("array", required(requestProperties, "/strategies/type").asText());
        assertEquals("string", required(requestProperties, "/strategies/items/type").asText());
        assertTrue(requestProperties.at("/strategies/items/enum").isMissingNode(),
                "strategies is currently inferred as strings, not a curated OpenAPI enum");

        JsonNode responseProperties = required(docs,
                "/components/schemas/RoutingComparisonResponse/properties");
        assertEquals("array", required(responseProperties, "/requestedStrategies/type").asText());
        assertEquals("string", required(responseProperties, "/requestedStrategies/items/type").asText());
        assertEquals("integer", required(responseProperties, "/candidateCount/type").asText());
        assertEquals("array", required(responseProperties, "/results/type").asText());
        assertRef(required(responseProperties, "/results/items"),
                "#/components/schemas/RoutingComparisonResultResponse");

        JsonNode resultProperties = required(docs,
                "/components/schemas/RoutingComparisonResultResponse/properties");
        assertEquals("string", required(resultProperties, "/strategyId/type").asText());
        assertEquals("string", required(resultProperties, "/status/type").asText());
        assertEquals("string", required(resultProperties, "/chosenServerId/type").asText());
        assertEquals("string", required(resultProperties, "/reason/type").asText());
        assertEquals("array", required(resultProperties, "/candidateServersConsidered/type").asText());
        assertEquals("object", required(resultProperties, "/scores/type").asText());
    }

    private JsonNode openApiDocs() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return OBJECT_MAPPER.readTree(body);
    }

    private static JsonNode required(JsonNode node, String pointer) {
        JsonNode value = node.at(pointer);
        assertFalse(value.isMissingNode(), () -> "Expected OpenAPI node at " + pointer);
        return value;
    }

    private static void assertRef(JsonNode schema, String expectedRef) {
        assertEquals(expectedRef, required(schema, "/$ref").asText());
    }
}
