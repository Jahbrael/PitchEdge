package com.betai.api;

import com.betai.api.dto.FootballDataFixtureSourceRegistrationRequest;
import com.betai.api.dto.FootballDataFixtureSourceRegistrationResponse;
import com.betai.api.dto.FixtureDiscoveryRequest;
import com.betai.api.dto.FixtureDiscoveryResponse;
import com.betai.api.dto.UpcomingFixtureImportRequest;
import com.betai.api.dto.UpcomingFixtureImportResponse;
import com.betai.api.dto.UpcomingFixtureResponse;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.match.MatchStatus;
import com.betai.repository.MatchRepository;
import com.betai.service.FixtureDiscoveryService;
import com.betai.service.FootballDataFixtureSourceService;
import com.betai.service.UpcomingFixtureImportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/fixtures")
public class AdminFixtureController {

    private final FootballDataFixtureSourceService footballDataFixtureSourceService;
    private final FixtureDiscoveryService fixtureDiscoveryService;
    private final UpcomingFixtureImportService upcomingFixtureImportService;
    private final MatchRepository matchRepository;
    private final Clock clock;

    @PostMapping("/sources/football-data/latest")
    public ResponseEntity<FootballDataFixtureSourceRegistrationResponse> registerFootballDataLatestFixtures(
            @Valid @RequestBody FootballDataFixtureSourceRegistrationRequest request
    ) {
        throw new ResponseStatusException(
                HttpStatus.GONE,
                "Football-Data fixture source registration is disabled. TheSportsDB is the active fixture source."
        );
    }

    @PostMapping("/import")
    public ResponseEntity<UpcomingFixtureImportResponse> importUpcomingFixtures(
            @Valid @RequestBody UpcomingFixtureImportRequest request
    ) {
        throw new ResponseStatusException(
                HttpStatus.GONE,
                "Legacy fixture import is disabled. Run the TheSportsDB pipeline refresh instead."
        );
    }

    @PostMapping("/discover")
    public ResponseEntity<FixtureDiscoveryResponse> discoverFixtures(
            @Valid @RequestBody FixtureDiscoveryRequest request
    ) {
        throw new ResponseStatusException(
                HttpStatus.GONE,
                "Legacy fixture discovery is disabled. Run the TheSportsDB pipeline refresh instead."
        );
    }

    @GetMapping("/upcoming")
    public ResponseEntity<List<UpcomingFixtureResponse>> upcomingFixtures(
            @RequestParam Set<LeagueCode> leagueCodes,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    ) {
        LocalDate from = fromDate == null ? LocalDate.now(clock) : fromDate;
        LocalDate to = toDate == null ? from.plusDays(14) : toDate;
        List<UpcomingFixtureResponse> fixtures = matchRepository.findCandidateFixtures(
                        leagueCodes,
                        from,
                        to,
                        List.of(MatchStatus.SCHEDULED)
                )
                .stream()
                .map(UpcomingFixtureResponse::from)
                .toList();
        return ResponseEntity.ok(fixtures);
    }
}
