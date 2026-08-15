package com.betai.api;

import com.betai.api.dto.DashboardLeagueSeasonStatusResponse;
import com.betai.api.dto.FixtureBrowserResponse;
import com.betai.api.dto.ModelAccuracyResponse;
import com.betai.api.dto.PredictionResponse;
import com.betai.api.dto.PredictionSelectionResponse;
import com.betai.domain.league.CompetitionHistoryPolicy;
import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.match.Match;
import com.betai.domain.match.MatchStatus;
import com.betai.domain.prediction.PredictionConfidenceBand;
import com.betai.domain.prediction.PredictionOutcome;
import com.betai.repository.LeagueRepository;
import com.betai.repository.MatchRepository;
import com.betai.repository.ModelAccuracyDailyRepository;
import com.betai.repository.OddsSnapshotRepository;
import com.betai.repository.PredictionSelectionRepository;
import com.betai.service.CompetitionHistoryPolicyService;
import com.betai.service.PredictionRunCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/platform")
@RequiredArgsConstructor
public class PlatformController {

    private static final int REQUIRED_DOMESTIC_SEASONS = 3;

    private final Clock clock;
    private final LeagueRepository leagueRepository;
    private final MatchRepository matchRepository;
    private final PredictionSelectionRepository predictionSelectionRepository;
    private final OddsSnapshotRepository oddsSnapshotRepository;
    private final ModelAccuracyDailyRepository modelAccuracyDailyRepository;
    private final CompetitionHistoryPolicyService competitionHistoryPolicyService;
    private final PredictionRunCacheService predictionRunCacheService;

    @GetMapping("/dashboard")
    @Transactional(readOnly = true)
    public ResponseEntity<PlatformDashboardResponse> dashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        LocalDate targetDate = date == null ? LocalDate.now(clock) : date;
        List<FixtureBrowserResponse> fixtures = fixturesForDate(targetDate);
        List<PredictionSelectionResponse> picks = predictionSelectionRepository
                .findSelectionsBetweenDates(targetDate, targetDate, PredictionOutcome.PENDING, PageRequest.of(0, 40))
                .stream()
                .map(PredictionSelectionResponse::from)
                .toList();
        List<PredictionSelectionResponse> bestPicks = picks.stream()
                .filter(PlatformController::veryHighConfidence)
                .sorted(Comparator.comparing(PlatformController::selectionProbability).reversed())
                .limit(8)
                .toList();
        List<PredictionSelectionResponse> valuePicks = predictionSelectionRepository
                .findPositiveValueSelectionsBetweenDates(targetDate, targetDate, PageRequest.of(0, 12))
                .stream()
                .map(PredictionSelectionResponse::from)
                .toList();
        List<PredictionResponse> recentRuns = predictionRunCacheService.recentRuns().stream()
                .limit(8)
                .toList();

        PlatformDashboardMetrics metrics = new PlatformDashboardMetrics(
                fixtures.size(),
                fixtures.stream().filter(FixtureBrowserResponse::hasPredictions).count(),
                bestPicks.size(),
                valuePicks.size(),
                fixtures.stream().filter(PlatformController::liveFixture).count(),
                fixtures.isEmpty()
                        ? BigDecimal.ZERO
                        : BigDecimal.valueOf(fixtures.stream().filter(FixtureBrowserResponse::hasOdds).count())
                        .divide(BigDecimal.valueOf(fixtures.size()), 4, RoundingMode.HALF_UP),
                latestFixtureRefresh(fixtures)
        );

        return ResponseEntity.ok(new PlatformDashboardResponse(
                OffsetDateTime.now(clock),
                targetDate,
                metrics,
                bestPicks,
                valuePicks,
                fixtures.stream().limit(100).toList(),
                recentRuns,
                dataHealth(metrics, recentRuns)
        ));
    }

    @GetMapping("/fixtures")
    @Transactional(readOnly = true)
    public ResponseEntity<List<FixtureBrowserResponse>> fixtures(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseEntity.ok(fixturesForDate(date == null ? LocalDate.now(clock) : date));
    }

    @GetMapping("/value-picks")
    @Transactional(readOnly = true)
    public ResponseEntity<List<PredictionSelectionResponse>> valuePicks(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        LocalDate fromDate = from == null ? LocalDate.now(clock) : from;
        LocalDate toDate = to == null ? fromDate.plusDays(14) : to;
        return ResponseEntity.ok(predictionSelectionRepository
                .findPositiveValueSelectionsBetweenDates(fromDate, toDate, PageRequest.of(0, 100))
                .stream()
                .map(PredictionSelectionResponse::from)
                .toList());
    }

    @GetMapping("/predictions")
    @Transactional(readOnly = true)
    public ResponseEntity<List<PredictionSelectionResponse>> predictions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        LocalDate fromDate = from == null ? LocalDate.now(clock) : from;
        LocalDate toDate = to == null ? fromDate.plusDays(14) : to;
        return ResponseEntity.ok(predictionSelectionRepository
                .findSelectionsBetweenDates(fromDate, toDate, PredictionOutcome.PENDING, PageRequest.of(0, 300))
                .stream()
                .map(PredictionSelectionResponse::from)
                .toList());
    }

    @GetMapping("/leagues")
    @Transactional(readOnly = true)
    public ResponseEntity<List<PlatformLeagueResponse>> leagues() {
        return ResponseEntity.ok(leagueRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(this::leagueResponse)
                .toList());
    }

    @GetMapping("/performance")
    @Transactional(readOnly = true)
    public ResponseEntity<PlatformPerformanceResponse> performance() {
        List<ModelAccuracyResponse> accuracyRows = modelAccuracyDailyRepository
                .findAll(PageRequest.of(0, 60, Sort.by(Sort.Direction.DESC, "accuracyDate")))
                .stream()
                .map(ModelAccuracyResponse::from)
                .toList();
        long settled = predictionSelectionRepository.countByOutcome(PredictionOutcome.WON)
                + predictionSelectionRepository.countByOutcome(PredictionOutcome.LOST)
                + predictionSelectionRepository.countByOutcome(PredictionOutcome.VOID);
        long won = predictionSelectionRepository.countByOutcome(PredictionOutcome.WON);
        long lost = predictionSelectionRepository.countByOutcome(PredictionOutcome.LOST);
        long priced = predictionSelectionRepository.countByBestOddsSnapshotIsNotNull();
        long positiveValue = predictionSelectionRepository.countByExpectedValueGreaterThan(BigDecimal.ZERO);
        return ResponseEntity.ok(new PlatformPerformanceResponse(
                settled,
                won,
                lost,
                priced,
                positiveValue,
                accuracyRows,
                settled < 30 ? "Not enough settled predictions yet." : null
        ));
    }

    @GetMapping("/runs/recent")
    @Transactional(readOnly = true)
    public ResponseEntity<List<PredictionResponse>> recentRuns() {
        return ResponseEntity.ok(predictionRunCacheService.recentRuns());
    }

    private List<FixtureBrowserResponse> fixturesForDate(LocalDate date) {
        List<Match> matches = matchRepository.findCandidateFixtures(
                Set.of(LeagueCode.values()),
                date,
                date,
                List.of(MatchStatus.values())
        );
        List<UUID> matchIds = matches.stream().map(Match::getId).toList();
        Set<UUID> matchesWithPredictions = matchIds.isEmpty()
                ? Set.of()
                : predictionSelectionRepository.findByMatch_IdInAndMarketDefinition_EnabledTrue(matchIds)
                .stream()
                .map(selection -> selection.getMatch().getId())
                .collect(Collectors.toSet());
        Set<UUID> matchesWithOdds = matchIds.isEmpty()
                ? Set.of()
                : oddsSnapshotRepository.findMatchIdsWithOdds(matchIds);

        return matches.stream()
                .map(match -> new FixtureBrowserResponse(
                        match.getId(),
                        match.getLeague().getCode().name(),
                        match.getLeague().getName(),
                        match.getLeague().getBadgeUrl(),
                        match.getLeague().getLogoUrl(),
                        match.getHomeTeam().getCanonicalName(),
                        match.getHomeTeam().getBadgeUrl(),
                        match.getHomeTeam().getLogoUrl(),
                        match.getAwayTeam().getCanonicalName(),
                        match.getAwayTeam().getBadgeUrl(),
                        match.getAwayTeam().getLogoUrl(),
                        match.getKickoffAt(),
                        match.getStatus(),
                        match.getHomeScore(),
                        match.getAwayScore(),
                        match.getLiveMinute(),
                        match.getVenue(),
                        matchesWithPredictions.contains(match.getId()),
                        matchesWithPredictions.contains(match.getId()) ? "Predictions ready" : "No prediction yet",
                        matchesWithOdds.contains(match.getId()),
                        matchesWithOdds.contains(match.getId()) ? "Odds available" : "No odds",
                        null,
                        match.getScoreRefreshedAt() != null ? match.getScoreRefreshedAt() : match.getUpdatedAt()
                ))
                .sorted(Comparator.comparingInt(PlatformController::fixturePriority)
                        .thenComparing(FixtureBrowserResponse::kickoffTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private PlatformLeagueResponse leagueResponse(League league) {
        CompetitionHistoryPolicy policy = competitionHistoryPolicyService.policyFor(league);
        int requiredHistoryUnits = policy == CompetitionHistoryPolicy.INTERNATIONAL_FOUR_YEAR_WINDOW
                ? CompetitionHistoryPolicyService.INTERNATIONAL_HISTORY_WINDOW_YEARS
                : REQUIRED_DOMESTIC_SEASONS;
        List<DashboardLeagueSeasonStatusResponse> seasonBreakdowns = matchRepository
                .summarizeSeasonsByLeagueCode(league.getCode())
                .stream()
                .map(summary -> new DashboardLeagueSeasonStatusResponse(
                        summary.getSeasonLabel(),
                        summary.getMatchCount(),
                        summary.getFinishedCount(),
                        summary.getScheduledCount(),
                        summary.getFirstMatchDate(),
                        summary.getLastMatchDate()
                ))
                .toList();
        long matches = matchRepository.countByLeague_Code(league.getCode());
        long scheduled = matchRepository.countByLeague_CodeAndStatus(league.getCode(), MatchStatus.SCHEDULED);
        long finished = matchRepository.countByLeague_CodeAndStatus(league.getCode(), MatchStatus.FINISHED);
        boolean predictionSelectable = league.isScrapeEnabled() && matches > 0;
        String importStatus = matches == 0 ? "IMPORT_PENDING" : "IMPORTED";
        String status = seasonBreakdowns.isEmpty()
                ? "PENDING"
                : (policy == CompetitionHistoryPolicy.LEAGUE_SEASONS && seasonBreakdowns.size() < requiredHistoryUnits
                ? "PARTIAL"
                : "COMPLETE");
        return new PlatformLeagueResponse(
                league.getCode().name(),
                league.getName(),
                league.getCountry(),
                league.getCurrentSeason(),
                league.getBadgeUrl(),
                league.getLogoUrl(),
                league.getPosterUrl(),
                policy.name(),
                requiredHistoryUnits,
                seasonBreakdowns.stream().map(DashboardLeagueSeasonStatusResponse::seasonLabel).toList(),
                seasonBreakdowns,
                matches,
                scheduled,
                finished,
                status,
                league.isScrapeEnabled(),
                predictionSelectable,
                importStatus,
                "TheSportsDB"
        );
    }

    private static boolean veryHighConfidence(PredictionSelectionResponse selection) {
        return PredictionConfidenceBand.VERY_HIGH.name().equals(selection.confidenceBand());
    }

    private static BigDecimal selectionProbability(PredictionSelectionResponse selection) {
        if (selection.probability() != null) {
            return selection.probability();
        }
        if (selection.tunedModelProbability() != null) {
            return selection.tunedModelProbability();
        }
        if (selection.calibratedProbability() != null) {
            return selection.calibratedProbability();
        }
        if (selection.rawModelProbability() != null) {
            return selection.rawModelProbability();
        }
        return BigDecimal.ZERO;
    }

    private static boolean liveFixture(FixtureBrowserResponse fixture) {
        return fixture.status() == MatchStatus.LIVE;
    }

    private static int fixturePriority(FixtureBrowserResponse fixture) {
        if (fixture.status() == MatchStatus.LIVE) {
            return 1;
        }
        if (fixture.status() == MatchStatus.SCHEDULED) {
            return 2;
        }
        return 3;
    }

    private static OffsetDateTime latestFixtureRefresh(List<FixtureBrowserResponse> fixtures) {
        return fixtures.stream()
                .map(FixtureBrowserResponse::lastRefreshedTime)
                .filter(time -> time != null)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private static PlatformDataHealth dataHealth(PlatformDashboardMetrics metrics, List<PredictionResponse> recentRuns) {
        String scores = metrics.latestScoreRefreshAt() == null
                ? "No local score refresh recorded"
                : "Latest local score refresh recorded";
        return new PlatformDataHealth(
                "Football data updated",
                "Odds coverage available",
                scores,
                recentRuns.isEmpty() ? "No recent prediction run recorded" : "Recent prediction run available"
        );
    }

    public record PlatformDashboardResponse(
            OffsetDateTime generatedAt,
            LocalDate date,
            PlatformDashboardMetrics metrics,
            List<PredictionSelectionResponse> bestPicks,
            List<PredictionSelectionResponse> valuePicks,
            List<FixtureBrowserResponse> fixtures,
            List<PredictionResponse> recentRuns,
            PlatformDataHealth dataHealth
    ) {}

    public record PlatformDashboardMetrics(
            long totalFixturesToday,
            long fixturesWithPredictions,
            long highConfidencePicks,
            long valuePicks,
            long liveFixtures,
            BigDecimal oddsCoverage,
            OffsetDateTime latestScoreRefreshAt
    ) {}

    public record PlatformDataHealth(
            String footballDataStatus,
            String oddsStatus,
            String scoreStatus,
            String automationStatus
    ) {}

    public record PlatformLeagueResponse(
            String leagueCode,
            String name,
            String country,
            String currentSeason,
            String leagueBadgeUrl,
            String leagueLogoUrl,
            String leaguePosterUrl,
            String historyPolicy,
            int requiredHistoryUnits,
            List<String> importedSeasonLabels,
            List<DashboardLeagueSeasonStatusResponse> seasonBreakdowns,
            long matches,
            long scheduledMatches,
            long finishedMatches,
            String historyStatus,
            boolean importEnabled,
            boolean predictionSelectable,
            String importStatus,
            String sourceUsed
    ) {}

    public record PlatformPerformanceResponse(
            long settledPredictions,
            long wonPredictions,
            long lostPredictions,
            long pricedSelections,
            long positiveValueSelections,
            List<ModelAccuracyResponse> accuracyRows,
            String emptyStateMessage
    ) {}
}
