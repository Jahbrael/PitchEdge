package com.betai;

import com.betai.api.dto.DashboardRunSummaryResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.time.OffsetDateTime;
import java.util.UUID;

@SpringBootTest
public class JsonSerializationTest {
    @Autowired
    private ObjectMapper mapper;

    @Test
    public void test() throws Exception {
        DashboardRunSummaryResponse run = new DashboardRunSummaryResponse(
            "PIPELINE", UUID.randomUUID(), "PREMIER_LEAGUE", "SUCCESS", OffsetDateTime.now(), OffsetDateTime.now(), 150L, "Summary", null, null
        );
        System.out.println("JSON_OUTPUT=" + mapper.writeValueAsString(run));
    }
}
