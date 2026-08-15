package com.betai.api;

import com.betai.api.dto.DailyOddsExtractionRequest;
import com.betai.api.dto.DailyOddsExtractionResponse;
import com.betai.api.dto.OddsExtractionRunResponse;
import com.betai.api.dto.OddsImportRequest;
import com.betai.api.dto.OddsImportResponse;
import com.betai.api.dto.OddsSnapshotResponse;
import com.betai.api.dto.PreMatchOddsRefreshRequest;
import com.betai.api.dto.PreMatchOddsRefreshResponse;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.market.MarketCode;
import com.betai.service.OddsImportService;
import com.betai.service.OddsSourceExtractionService;
import com.betai.service.PreMatchOddsRefreshService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/odds")
public class AdminOddsController {

    private final OddsImportService oddsImportService;
    private final OddsSourceExtractionService oddsSourceExtractionService;
    private final PreMatchOddsRefreshService preMatchOddsRefreshService;

    @PostMapping("/import")
    public ResponseEntity<OddsImportResponse> importOdds(@Valid @RequestBody OddsImportRequest request) {
        return ResponseEntity.ok(oddsImportService.importOdds(request));
    }

    @PostMapping("/extraction/raw-snapshots/{rawSnapshotId}")
    public ResponseEntity<OddsExtractionRunResponse> extractRawOddsSnapshot(
            @PathVariable UUID rawSnapshotId,
            @RequestParam(defaultValue = "false") boolean forceReprocess,
            @RequestParam(defaultValue = "true") boolean recalculateExistingSelections
    ) {
        throw new ResponseStatusException(
                HttpStatus.GONE,
                "Raw odds snapshot extraction is disabled. Odds refresh must use The Odds API source targets."
        );
    }

    @PostMapping("/extraction/daily")
    public ResponseEntity<DailyOddsExtractionResponse> extractDailyOddsSnapshots(
            @Valid @RequestBody DailyOddsExtractionRequest request
    ) {
        return ResponseEntity.ok(oddsSourceExtractionService.extractDailyOddsSnapshots(request));
    }

    @PostMapping("/pre-match/refresh")
    public ResponseEntity<PreMatchOddsRefreshResponse> refreshPreMatchOdds(
            @Valid @RequestBody PreMatchOddsRefreshRequest request
    ) {
        return ResponseEntity.ok(preMatchOddsRefreshService.refreshPreMatchOdds(request));
    }

    @GetMapping("/snapshots")
    public ResponseEntity<List<OddsSnapshotResponse>> snapshots(
            @RequestParam(required = false) LeagueCode leagueCode,
            @RequestParam(required = false) MarketCode marketCode,
            @RequestParam(required = false) UUID matchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return ResponseEntity.ok(oddsImportService.findSnapshots(
                leagueCode,
                marketCode,
                matchId,
                fromDate,
                toDate,
                limit
        ));
    }
}
