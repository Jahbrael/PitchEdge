package com.betai.api;

import com.betai.api.dto.DailyRefreshRequest;
import com.betai.api.dto.DailyRefreshResponse;
import com.betai.service.DailyRefreshService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/refresh")
public class AdminRefreshController {

    private final DailyRefreshService dailyRefreshService;

    @PostMapping("/daily")
    public ResponseEntity<DailyRefreshResponse> triggerDailyRefresh(@Valid @RequestBody DailyRefreshRequest request) {
        throw new ResponseStatusException(
                HttpStatus.GONE,
                "Legacy scrape refresh is disabled. Run the TheSportsDB pipeline refresh instead."
        );
    }
}
