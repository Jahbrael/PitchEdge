package com.betai.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class ProtectedExportIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unauthenticatedExportFails() throws Exception {
        String jsonPayload = "{\"fixtureDateFrom\":\"2026-01-01\",\"fixtureDateTo\":\"2026-01-02\",\"batchCount\":1,\"selectionsPerBatch\":1}";

        mockMvc.perform(post("/api/v1/predictions/form/export")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isForbidden());
    }
}
