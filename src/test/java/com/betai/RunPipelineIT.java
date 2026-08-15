package com.betai;

import com.betai.api.dto.FullPipelineRequest;
import com.betai.api.dto.FullPipelineResponse;
import com.betai.domain.league.LeagueCode;
import com.betai.service.FullPipelineOrchestrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;

@SpringBootTest
public class RunPipelineIT {

    @Autowired
    private FullPipelineOrchestrationService pipelineService;

    @Test
    public void runPipeline() {
        System.out.println("Starting pipeline run...");
        FullPipelineRequest request = new FullPipelineRequest(
                Set.of(LeagueCode.PREMIER_LEAGUE),
                null, null, null, null, null, null, null, null, null,
                false, false, false, null, false, false, false, false, true, false, false, false, false,
                null, false, false, false, true, false, null, null, null
        );
        FullPipelineResponse response = pipelineService.runPipeline(request);
        System.out.println("Pipeline completed. Status: " + response.status());
        System.out.println("Summary: " + response.steps());
    }
}
