package com.betai.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class CsrfIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void protectedEndpointFailsWithoutCsrf() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout").with(user("testuser")))
                .andExpect(status().isForbidden());
    }

    @Test
    void protectedEndpointSucceedsWithCsrf() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout").with(user("testuser")).with(csrf()))
                .andExpect(status().isOk());
    }
}
