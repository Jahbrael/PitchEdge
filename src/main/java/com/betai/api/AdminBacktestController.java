package com.betai.api;

import com.betai.api.dto.BacktestRequest;
import com.betai.api.dto.BacktestResponse;
import com.betai.service.BacktestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/backtesting")
public class AdminBacktestController {

    private final BacktestService backtestService;

    @PostMapping("/run")
    public ResponseEntity<BacktestResponse> runBacktest(@Valid @RequestBody BacktestRequest request) {
        return ResponseEntity.ok(backtestService.runBacktest(request));
    }
}
