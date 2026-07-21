package org.scholar.directory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.security.api-token=test-api-token")
@AutoConfigureMockMvc
class ApiSecurityIntegrationTest {
    @Autowired MockMvc mvc;

    @Test
    void apiTokenIsRequiredWhenConfigured() throws Exception {
        mvc.perform(get("/api/v1/system/index-status"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mvc.perform(get("/api/v1/system/index-status")
                        .header("Authorization", "Bearer test-api-token"))
                .andExpect(status().isOk());
    }

    @Test
    void oversizedRequestIsRejectedBeforeDeserialization() throws Exception {
        String body = "{\"request\":\"" + "x".repeat(70_000) + "\"}";
        mvc.perform(post("/api/v1/ai/article-search/parse")
                        .header("Authorization", "Bearer test-api-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
    }

    @Test
    void actuatorRequiresTheConfiguredToken() throws Exception {
        mvc.perform(get("/actuator"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/health")
                        .header("Authorization", "Bearer test-api-token"))
                .andExpect(status().isOk());
    }
}
