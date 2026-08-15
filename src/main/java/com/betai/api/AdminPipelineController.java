package com.betai.api;

import com.betai.api.dto.FullPipelineRequest;
import com.betai.api.dto.FullPipelineResponse;
import com.betai.service.FullPipelineOrchestrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/pipeline")
public class AdminPipelineController {

    private final FullPipelineOrchestrationService fullPipelineOrchestrationService;

    @Deprecated
    @PostMapping("/run")
    public ResponseEntity<?> runPipeline(@Valid @RequestBody FullPipelineRequest request) {
        // We will start it in a background thread to prevent browser timeouts
        Thread thread = new Thread(() -> {
            try {
                fullPipelineOrchestrationService.runPipeline(request);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        thread.start();
        
        return ResponseEntity.ok().body("{\"status\": \"SUCCESS\", \"message\": \"Pipeline successfully started in the background.\"}");
    }
}
