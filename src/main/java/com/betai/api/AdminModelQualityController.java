package com.betai.api;

import com.betai.api.dto.LeagueMarketReadinessResponse;
import com.betai.api.dto.ModelQualityGenerationRequest;
import com.betai.api.dto.ModelQualityGenerationResponse;
import com.betai.api.dto.ModelQualitySnapshotResponse;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.market.MarketCode;
import com.betai.service.ModelQualityService;
import com.betai.service.ModelReadinessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/model-quality")
public class AdminModelQualityController {

    private final ModelQualityService modelQualityService;
    private final ModelReadinessService modelReadinessService;

    @PostMapping("/generate")
    public ResponseEntity<ModelQualityGenerationResponse> generate(@Valid @RequestBody ModelQualityGenerationRequest request) {
        return ResponseEntity.ok(modelQualityService.generateQualitySnapshots(request));
    }

    @GetMapping
    public ResponseEntity<List<ModelQualitySnapshotResponse>> list(
            @RequestParam LeagueCode leagueCode,
            @RequestParam(required = false) String modelVersion,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate qualityDate
    ) {
        return ResponseEntity.ok(modelQualityService.getQualitySnapshots(leagueCode, modelVersion, qualityDate));
    }

    @GetMapping("/readiness")
    public ResponseEntity<List<LeagueMarketReadinessResponse>> readiness(
            @RequestParam(required = false) Set<LeagueCode> leagueCodes,
            @RequestParam(required = false) Set<MarketCode> marketCodes,
            @RequestParam(required = false) String modelVersion,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate
    ) {
        return ResponseEntity.ok(modelReadinessService.getReadiness(leagueCodes, marketCodes, modelVersion, asOfDate));
    }
}
