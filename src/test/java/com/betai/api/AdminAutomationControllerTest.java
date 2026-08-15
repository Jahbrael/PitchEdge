package com.betai.api;

import com.betai.automation.DailyPipelineScheduler;
import com.betai.domain.automation.AutomationTriggerType;
import com.betai.api.dto.AutomationProgressResponse;
import com.betai.service.AutomationProgressService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import com.betai.automation.DailyPipelineScheduler;
import com.betai.domain.automation.AutomationTriggerType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class AdminAutomationControllerTest {

    @Mock
    private DailyPipelineScheduler dailyPipelineScheduler;
    @Mock
    private AutomationProgressService automationProgressService;

    private AdminAutomationController adminAutomationController;
    private AdminAutomationProgressController adminAutomationProgressController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        adminAutomationController = new AdminAutomationController(dailyPipelineScheduler);
        adminAutomationProgressController = new AdminAutomationProgressController(automationProgressService);
    }

    @Test
    void testRunAutomationNow_Accepted() {
        when(dailyPipelineScheduler.triggerPipeline(AutomationTriggerType.MANUAL_ADMIN_TRIGGER)).thenReturn(true);

        ResponseEntity<Map<String, Object>> response = adminAutomationController.runAutomationNow();

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertEquals("ACCEPTED", response.getBody().get("status"));
        assertEquals("MANUAL_ADMIN_TRIGGER", response.getBody().get("triggerType"));
        assertEquals(false, response.getBody().get("alreadyRunning"));

        verify(dailyPipelineScheduler, times(1)).triggerPipeline(AutomationTriggerType.MANUAL_ADMIN_TRIGGER);
    }

    @Test
    void testRunAutomationNow_Rejected_AlreadyRunning() {
        when(dailyPipelineScheduler.triggerPipeline(AutomationTriggerType.MANUAL_ADMIN_TRIGGER)).thenReturn(false);

        ResponseEntity<Map<String, Object>> response = adminAutomationController.runAutomationNow();

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("REJECTED", response.getBody().get("status"));
        assertEquals("Automation is already running.", response.getBody().get("message"));
        assertEquals(true, response.getBody().get("alreadyRunning"));

        verify(dailyPipelineScheduler, times(1)).triggerPipeline(AutomationTriggerType.MANUAL_ADMIN_TRIGGER);
    }

    @Test
    void returnsBackendAutomationProgress() {
        AutomationProgressResponse progress = new AutomationProgressResponse(
                null, "NOT_STARTED", "Not Started", 0, null, 0, 8,
                null, null, null, null, java.util.List.of()
        );
        when(automationProgressService.latestProgress()).thenReturn(progress);

        assertEquals(progress, adminAutomationProgressController.automationProgress());
        verify(automationProgressService).latestProgress();
    }
}
