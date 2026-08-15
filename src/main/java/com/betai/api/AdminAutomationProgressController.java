package com.betai.api;

import com.betai.api.dto.AutomationProgressResponse;
import com.betai.service.AutomationProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/automation")
public class AdminAutomationProgressController {

    private final AutomationProgressService automationProgressService;

    @GetMapping("/progress")
    public AutomationProgressResponse automationProgress() {
        return automationProgressService.latestProgress();
    }
}
