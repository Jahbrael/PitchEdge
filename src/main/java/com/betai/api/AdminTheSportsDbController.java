package com.betai.api;

import com.betai.api.dto.TheSportsDbCoverageRefreshRequest;
import com.betai.api.dto.TheSportsDbCoverageResponse;
import com.betai.api.dto.TheSportsDbEventStatsImportRequest;
import com.betai.api.dto.TheSportsDbHealthResponse;
import com.betai.api.dto.TheSportsDbLeagueSeasonImportRequest;
import com.betai.api.dto.TheSportsDbArtworkBackfillRequest;
import com.betai.api.dto.TheSportsDbArtworkBackfillResponse;
import com.betai.integration.thesportsdb.dto.TheSportsDbEventStatisticsImportSummary;
import com.betai.integration.thesportsdb.dto.TheSportsDbImportSummary;
import com.betai.integration.thesportsdb.service.TheSportsDbArtworkBackfillService;
import com.betai.integration.thesportsdb.service.TheSportsDbCoverageService;
import com.betai.integration.thesportsdb.service.TheSportsDbEventEnrichmentService;
import com.betai.integration.thesportsdb.service.TheSportsDbLeagueSeasonImportService;
import com.betai.service.TheSportsDbAdminDiagnosticsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.betai.domain.league.LeagueCode;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/integrations/thesportsdb")
public class AdminTheSportsDbController {

    private final TheSportsDbLeagueSeasonImportService leagueSeasonImportService;
    private final TheSportsDbEventEnrichmentService eventEnrichmentService;
    private final TheSportsDbCoverageService coverageService;
    private final TheSportsDbArtworkBackfillService artworkBackfillService;
    private final TheSportsDbAdminDiagnosticsService diagnosticsService;

    @GetMapping("/health")
    public ResponseEntity<TheSportsDbHealthResponse> health() {
        return ResponseEntity.ok(diagnosticsService.health());
    }

    @PostMapping("/league-season/import")
    public ResponseEntity<TheSportsDbImportSummary> importLeagueSeason(
            @Valid @RequestBody TheSportsDbLeagueSeasonImportRequest request
    ) {
        return ResponseEntity.ok(leagueSeasonImportService.importLeagueSeason(
                request.leagueCode(),
                request.externalLeagueId(),
                request.season()
        ));
    }

    @PostMapping("/event-statistics/import")
    public ResponseEntity<TheSportsDbEventStatisticsImportSummary> importEventStatistics(
            @Valid @RequestBody TheSportsDbEventStatsImportRequest request
    ) {
        return ResponseEntity.ok(eventEnrichmentService.importEventStatistics(request.externalEventId()));
    }

    @PostMapping("/coverage/recalculate")
    public ResponseEntity<TheSportsDbCoverageResponse> recalculateCoverage(
            @Valid @RequestBody TheSportsDbCoverageRefreshRequest request
    ) {
        return ResponseEntity.ok(TheSportsDbCoverageResponse.from(
                coverageService.recalculate(request.leagueCode(), request.season())
        ));
    }

    @PostMapping("/artwork/backfill")
    public ResponseEntity<TheSportsDbArtworkBackfillResponse> backfillArtwork(
            @RequestParam(required = false) LeagueCode leagueCode,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String teamExternalKey,
            @RequestParam(required = false) Boolean dryRun,
            @RequestParam(required = false) Boolean teamsOnly,
            @RequestParam(required = false) Boolean leaguesOnly,
            @RequestBody(required = false) TheSportsDbArtworkBackfillRequest body
    ) {
        Set<LeagueCode> codes = body != null && body.leagueCodes() != null ? body.leagueCodes() : null;
        LeagueCode code = body != null && body.leagueCode() != null ? body.leagueCode() : leagueCode;
        Integer lim = body != null && body.limit() != null ? body.limit() : limit;
        String teamKey = body != null && body.teamExternalKey() != null ? body.teamExternalKey() : teamExternalKey;
        Boolean dry = body != null && body.dryRun() != null ? body.dryRun() : dryRun;
        Boolean tOnly = body != null && body.teamsOnly() != null ? body.teamsOnly() : teamsOnly;
        Boolean lOnly = body != null && body.leaguesOnly() != null ? body.leaguesOnly() : leaguesOnly;

        TheSportsDbArtworkBackfillRequest merged = new TheSportsDbArtworkBackfillRequest(
                codes, code, lim, teamKey, dry, tOnly, lOnly
        );
        return ResponseEntity.ok(artworkBackfillService.backfillArtwork(merged));
    }
}
