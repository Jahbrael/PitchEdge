package com.betai.api;

import com.betai.automation.DailyPipelineScheduler;
import com.betai.domain.automation.AutomationTriggerType;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "bet-ai.automation", name = "enabled", havingValue = "true")
@RequestMapping("/api/v1/admin/automation")
public class AdminAutomationController {

    private final DailyPipelineScheduler dailyPipelineScheduler;

    @PostMapping("/runNow")
    public ResponseEntity<Map<String, Object>> runAutomationNow() {
        boolean started = dailyPipelineScheduler.triggerPipeline(AutomationTriggerType.MANUAL_ADMIN_TRIGGER);
        
        Map<String, Object> response = new HashMap<>();
        response.put("startedTime", OffsetDateTime.now());
        response.put("triggerType", AutomationTriggerType.MANUAL_ADMIN_TRIGGER.name());

        if (started) {
            response.put("status", "ACCEPTED");
            response.put("message", "Automation successfully started in the background.");
            response.put("alreadyRunning", false);
            return ResponseEntity.accepted().body(response);
        } else {
            response.put("status", "REJECTED");
            response.put("message", "Automation is already running.");
            response.put("alreadyRunning", true);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }
}
