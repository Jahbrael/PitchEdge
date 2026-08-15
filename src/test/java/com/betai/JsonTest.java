package com.betai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@AutoConfigureMockMvc
public class JsonTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testOverview() throws Exception {
        String json = mockMvc.perform(get("/api/v1/admin/dashboard/overview")
                .header("X-BETAI-ADMIN-KEY", "local-dev-admin-key"))
                .andReturn().getResponse().getContentAsString();
        System.out.println("JSON_RESPONSE=" + json);
    }
}
