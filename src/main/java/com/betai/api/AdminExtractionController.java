package com.betai.api;

import com.betai.api.dto.DailyExtractionRequest;
import com.betai.api.dto.DailyExtractionResponse;
import com.betai.api.dto.ExtractionRunResponse;
import com.betai.service.ExtractionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/extraction")
public class AdminExtractionController {

    private final ExtractionService extractionService;

    @PostMapping("/raw-snapshots/{rawSnapshotId}")
    public ResponseEntity<ExtractionRunResponse> extractRawSnapshot(
            @PathVariable UUID rawSnapshotId,
            @RequestParam(defaultValue = "false") boolean forceReprocess
    ) {
        throw new ResponseStatusException(
                HttpStatus.GONE,
                "Legacy scrape extraction is disabled. TheSportsDB is the active football data source."
        );
    }

    @PostMapping("/daily")
    public ResponseEntity<DailyExtractionResponse> extractDailySnapshots(@Valid @RequestBody DailyExtractionRequest request) {
        throw new ResponseStatusException(
                HttpStatus.GONE,
                "Legacy scrape extraction is disabled. TheSportsDB is the active football data source."
        );
    }
}
