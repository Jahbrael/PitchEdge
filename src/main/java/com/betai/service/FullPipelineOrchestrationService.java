package com.betai.service;

import com.betai.api.dto.FullPipelineRequest;
import com.betai.api.dto.FullPipelineResponse;

public interface FullPipelineOrchestrationService {

    FullPipelineResponse runPipeline(FullPipelineRequest request);
}
