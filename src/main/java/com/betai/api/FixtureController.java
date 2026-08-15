package com.betai.api;

import com.betai.api.dto.FixtureBrowserResponse;
import com.betai.api.dto.FixtureScoreRefreshSummary;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.match.Match;
import com.betai.domain.match.MatchStatus;
import com.betai.repository.MatchRepository;
import com.betai.repository.OddsSnapshotRepository;
import com.betai.repository.PredictionSelectionRepository;
import com.betai.service.FixtureScoreRefreshService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/fixtures")
@RequiredArgsConstructor
public class FixtureController {

    private final MatchRepository matchRepository;
    private final PredictionSelectionRepository predictionSelectionRepository;
    private final OddsSnapshotRepository oddsSnapshotRepository;
    private final FixtureScoreRefreshService fixtureScoreRefreshService;
    private final Clock clock;

    @GetMapping
    public ResponseEntity<List<FixtureBrowserResponse>> getFixtures(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Set<LeagueCode> leagueCodes,
            @RequestParam(required = false) Set<MatchStatus> statuses
    ) {
        LocalDate searchDate = date != null ? date : LocalDate.now(clock);

        List<Match> matches;
        if (leagueCodes != null && !leagueCodes.isEmpty() && statuses != null && !statuses.isEmpty()) {
            matches = matchRepository.findCandidateFixtures(leagueCodes, searchDate, searchDate, List.copyOf(statuses));
        } else if (leagueCodes != null && !leagueCodes.isEmpty()) {
            matches = matchRepository.findCandidateFixtures(leagueCodes, searchDate, searchDate, List.of(MatchStatus.values()));
        } else if (statuses != null && !statuses.isEmpty()) {
            matches = matchRepository.findCandidateFixtures(Set.of(LeagueCode.values()), searchDate, searchDate, List.copyOf(statuses));
        } else {
            matches = matchRepository.findCandidateFixtures(Set.of(LeagueCode.values()), searchDate, searchDate, List.of(MatchStatus.values()));
        }

        List<UUID> matchIds = matches.stream().map(Match::getId).toList();

        Set<UUID> matchesWithPredictions = predictionSelectionRepository
                .findByMatch_IdInAndMarketDefinition_EnabledTrue(matchIds)
                .stream().map(s -> s.getMatch().getId()).collect(Collectors.toSet());

        Set<UUID> matchesWithOdds = oddsSnapshotRepository.findMatchIdsWithOdds(matchIds);

        List<FixtureBrowserResponse> responses = matches.stream().map(m -> {
            boolean hasPred = matchesWithPredictions.contains(m.getId());
            boolean hasOdds = matchesWithOdds.contains(m.getId());
            String predStatus = hasPred ? "Predictions ready" : "No prediction yet";
            String oddsProv = hasOdds ? "Odds available" : "No odds";

            return new FixtureBrowserResponse(
                    m.getId(),
                    m.getLeague().getCode().name(),
                    m.getLeague().getName(),
                    m.getLeague().getBadgeUrl(),
                    m.getLeague().getLogoUrl(),
                    m.getHomeTeam().getCanonicalName(),
                    m.getHomeTeam().getBadgeUrl(),
                    m.getHomeTeam().getLogoUrl(),
                    m.getAwayTeam().getCanonicalName(),
                    m.getAwayTeam().getBadgeUrl(),
                    m.getAwayTeam().getLogoUrl(),
                    m.getKickoffAt(),
                    m.getStatus(),
                    m.getHomeScore(),
                    m.getAwayScore(),
                    m.getLiveMinute(),
                    m.getVenue(),
                    hasPred,
                    predStatus,
                    hasOdds,
                    oddsProv,
                    null, // latestOddsSnapshotTime
                    m.getScoreRefreshedAt() != null ? m.getScoreRefreshedAt() : m.getUpdatedAt()
            );
        }).toList();

        return ResponseEntity.ok(responses);
    }

    @PostMapping("/scores/refresh")
    public ResponseEntity<FixtureScoreRefreshSummary> refreshScores(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Set<LeagueCode> leagueCodes
    ) {
        LocalDate searchDate = date != null ? date : LocalDate.now(clock);
        FixtureScoreRefreshSummary summary = fixtureScoreRefreshService.refreshScores(searchDate, leagueCodes);
        if ("Score refresh already running".equals(summary.safeFailureReason())) {
            return ResponseEntity.status(409).body(summary);
        }
        return ResponseEntity.ok(summary);
    }
}
