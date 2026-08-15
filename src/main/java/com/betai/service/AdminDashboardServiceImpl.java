package com.betai.service;

import com.betai.api.dto.DashboardLeagueStatusResponse;
import com.betai.api.dto.DashboardLeagueSeasonStatusResponse;
import com.betai.api.dto.DashboardOverviewResponse;
import com.betai.api.dto.DashboardRunSummaryResponse;
import com.betai.api.dto.DashboardSourceHealthResponse;
import com.betai.api.dto.DashboardTotalsResponse;
import com.betai.domain.automation.AutomationRun;
import com.betai.domain.automation.AutomationRunStatus;
import com.betai.domain.extraction.ExtractionRun;
import com.betai.domain.feature.FeatureGenerationRun;
import com.betai.domain.league.CompetitionHistoryPolicy;
import com.betai.domain.league.League;
import com.betai.domain.match.MatchStatus;
import com.betai.domain.odds.OddsExtractionRun;
import com.betai.domain.pipeline.PipelineRun;
import com.betai.domain.pipeline.PipelineStatus;
import com.betai.domain.prediction.PredictionGenerationRun;
import com.betai.domain.prediction.PredictionOutcome;
import com.betai.domain.refresh.DataRefreshLog;
import com.betai.domain.refresh.RefreshStatus;
import com.betai.domain.backtest.BacktestRun;
import com.betai.domain.settlement.SettlementRun;
import com.betai.domain.source.SourceTarget;
import com.betai.repository.AutomationRunRepository;
import com.betai.repository.DataRefreshLogRepository;
import com.betai.repository.ExtractionRunRepository;
import com.betai.repository.FeatureGenerationRunRepository;
import com.betai.repository.LeagueRepository;
import com.betai.repository.MarketDefinitionRepository;
import com.betai.repository.MatchRepository;
import com.betai.repository.ModelAccuracyDailyRepository;
import com.betai.repository.ModelTuningProfileRepository;
import com.betai.repository.BookmakerRepository;
import com.betai.repository.OddsSnapshotRepository;
import com.betai.repository.BacktestRunRepository;
import com.betai.repository.OddsExtractionRunRepository;
import com.betai.repository.PipelineRunRepository;
import com.betai.repository.PredictionGenerationRunRepository;
import com.betai.repository.PredictionSelectionRepository;
import com.betai.repository.SettlementRunRepository;
import com.betai.repository.SourceTargetRepository;
import com.betai.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private static final int RECENT_RUN_LIMIT = 200;
    private static final int SOURCE_HEALTH_LIMIT = 50;
    private static final int SOURCE_FAILURE_ALERT_THRESHOLD = 3;
    private static final String DUPLICATE_SUCCESS_REFRESH_CONSTRAINT = "ux_data_refresh_logs_one_success_per_league_date";
    private static final int REQUIRED_DOMESTIC_SEASONS = 3;
    private static final long MINIMUM_FINISHED_MATCHES_FOR_COVERAGE = 30;
    private static final long MINIMUM_SCHEDULED_MATCHES_FOR_FIXTURE_COVERAGE = 3;

    private final Clock clock;
    private final LeagueRepository leagueRepository;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final SourceTargetRepository sourceTargetRepository;
    private final DataRefreshLogRepository dataRefreshLogRepository;
    private final ExtractionRunRepository extractionRunRepository;
    private final FeatureGenerationRunRepository featureGenerationRunRepository;
    private final PredictionGenerationRunRepository predictionGenerationRunRepository;
    private final SettlementRunRepository settlementRunRepository;
    private final PredictionSelectionRepository predictionSelectionRepository;
    private final MarketDefinitionRepository marketDefinitionRepository;
    private final ModelAccuracyDailyRepository modelAccuracyDailyRepository;
    private final BookmakerRepository bookmakerRepository;
    private final OddsSnapshotRepository oddsSnapshotRepository;
    private final ModelTuningProfileRepository modelTuningProfileRepository;
    private final OddsExtractionRunRepository oddsExtractionRunRepository;
    private final PipelineRunRepository pipelineRunRepository;
    private final BacktestRunRepository backtestRunRepository;
    private final AutomationRunRepository automationRunRepository;
    private final CompetitionHistoryPolicyService competitionHistoryPolicyService;

    @Override
    @Transactional(readOnly = true)
    public DashboardOverviewResponse getOverview() {
        List<League> leagues = leagueRepository.findAll().stream()
                .sorted(Comparator.comparing(league -> league.getCode().name()))
                .toList();
        List<SourceTarget> sourceTargets = sourceTargetRepository.findTop50ByActiveTrueOrderByConsecutiveFailuresDescLastFailureAtDescNameAsc();

        DashboardTotalsResponse totals = totals(leagues);
        List<PipelineRun> recentPipelineRuns = pipelineRunRepository.findTop10ByOrderByStartedAtDesc();
        List<DashboardLeagueStatusResponse> leagueStatuses = leagues.stream()
                .map(league -> leagueStatus(league, recentPipelineRuns))
                .toList();
        List<DashboardSourceHealthResponse> sourceHealth = sourceTargets.stream()
                .filter(this::shouldShowSourceHealth)
                .limit(SOURCE_HEALTH_LIMIT)
                .map(this::sourceHealth)
                .toList();
        List<DashboardRunSummaryResponse> recentRuns = recentRuns(recentPipelineRuns);
        List<String> alerts = alerts(totals, leagueStatuses, sourceHealth, recentRuns);

        return new DashboardOverviewResponse(
                OffsetDateTime.now(clock),
                dashboardStatus(alerts, sourceHealth, recentRuns),
                totals,
                leagueStatuses,
                sourceHealth,
                recentRuns,
                alerts
        );
    }

    private DashboardTotalsResponse totals(List<League> leagues) {
        return new DashboardTotalsResponse(
                leagues.size(),
                leagues.stream().filter(League::isActive).count(),
                marketDefinitionRepository.countByEnabledTrue(),
                sourceTargetRepository.count(),
                sourceTargetRepository.countByActiveTrue(),
                teamRepository.count(),
                matchRepository.count(),
                matchRepository.countByStatus(MatchStatus.SCHEDULED),
                matchRepository.countByStatus(MatchStatus.FINISHED),
                predictionSelectionRepository.count(),
                predictionSelectionRepository.countByOutcome(PredictionOutcome.PENDING),
                predictionSelectionRepository.countByOutcome(PredictionOutcome.WON),
                predictionSelectionRepository.countByOutcome(PredictionOutcome.LOST),
                predictionSelectionRepository.countByOutcome(PredictionOutcome.VOID),
                modelAccuracyDailyRepository.count(),
                bookmakerRepository.count(),
                bookmakerRepository.countByActiveTrue(),
                oddsSnapshotRepository.count(),
                predictionSelectionRepository.countByBestOddsSnapshotIsNotNull(),
                predictionSelectionRepository.countByExpectedValueGreaterThan(BigDecimal.ZERO),
                modelTuningProfileRepository.count(),
                modelTuningProfileRepository.countByActiveTrue(),
                automationRunRepository.count(),
                automationRunRepository.countByRunStatus(AutomationRunStatus.FAILED)
        );
    }

    private DashboardLeagueStatusResponse leagueStatus(League league, List<PipelineRun> recentPipelineRuns) {
        long matches = matchRepository.countByLeague_Code(league.getCode());
        long scheduledMatches = matchRepository.countByLeague_CodeAndStatus(league.getCode(), MatchStatus.SCHEDULED);
        long finishedMatches = matchRepository.countByLeague_CodeAndStatus(league.getCode(), MatchStatus.FINISHED);
        long sourceTargets = sourceTargetRepository.countByLeague_Code(league.getCode());
        long activeSourceTargets = sourceTargetRepository.countByLeague_CodeAndActiveTrue(league.getCode());
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
        CompetitionHistoryPolicy historyPolicy = competitionHistoryPolicyService.policyFor(league);
        int requiredSeasonCount = requiredSeasonCount(historyPolicy);
        String historyStatus = historyStatus(historyPolicy, requiredSeasonCount, seasonBreakdowns, activeSourceTargets);
        String coverageStatus = dataCoverageStatus(league, finishedMatches, scheduledMatches, activeSourceTargets);
        LatestRefreshSummary latestRefresh = latestRefreshSummary(league, matches, recentPipelineRuns);

        return new DashboardLeagueStatusResponse(
                league.getCode().name(),
                league.getName(),
                league.getCountry(),
                league.getCurrentSeason(),
                historyPolicy.name(),
                requiredSeasonCount,
                seasonBreakdowns.size(),
                seasonBreakdowns.stream().map(DashboardLeagueSeasonStatusResponse::seasonLabel).toList(),
                seasonBreakdowns,
                historyStatus,
                "TheSportsDB",
                league.isActive(),
                league.isScrapeEnabled(),
                teamRepository.countByLeague_Code(league.getCode()),
                matches,
                scheduledMatches,
                finishedMatches,
                sourceTargets,
                activeSourceTargets,
                coverageStatus,
                dataCoverageMessage(league, finishedMatches, scheduledMatches, activeSourceTargets, coverageStatus),
                latestRefresh == null ? "NEVER_RUN" : latestRefresh.status(),
                latestRefresh == null ? null : latestRefresh.startedAt(),
                latestRefresh == null ? null : latestRefresh.finishedAt(),
                latestRefresh == null ? null : latestRefresh.durationMs(),
                latestRefresh == null ? null : latestRefresh.failureReason()
        );
    }

    private LatestRefreshSummary latestRefreshSummary(
            League league,
            long matches,
            List<PipelineRun> recentPipelineRuns
    ) {
        DataRefreshLog latestRefresh = effectiveLatestRefresh(league);
        if (latestRefresh != null) {
            return new LatestRefreshSummary(
                    latestRefresh.getRefreshStatus().name(),
                    latestRefresh.getStartedAt(),
                    latestRefresh.getFinishedAt(),
                    latestRefresh.getDurationMs(),
                    latestRefresh.getFailureReason()
            );
        }
        if (matches <= 0) {
            return null;
        }
        return recentPipelineRuns.stream()
                .filter(run -> pipelineRunIncludesLeague(run, league))
                .findFirst()
                .map(run -> new LatestRefreshSummary(
                        refreshStatusFromPipeline(run.getPipelineStatus()),
                        run.getStartedAt(),
                        run.getFinishedAt(),
                        run.getDurationMs(),
                        run.getFailureReason()
                ))
                .orElse(null);
    }

    private String refreshStatusFromPipeline(PipelineStatus status) {
        return status == null ? "UNKNOWN" : status.name();
    }

    private boolean pipelineRunIncludesLeague(PipelineRun run, League league) {
        if (run == null || run.getLeagueCodes() == null || run.getLeagueCodes().isBlank()) {
            return false;
        }
        return ("," + run.getLeagueCodes() + ",").contains("," + league.getCode().name() + ",");
    }

    private DataRefreshLog effectiveLatestRefresh(League league) {
        DataRefreshLog latestRefresh = dataRefreshLogRepository
                .findFirstByLeague_CodeOrderByStartedAtDesc(league.getCode())
                .orElse(null);
        if (!duplicateSuccessRefreshFailure(latestRefresh)) {
            return latestRefresh;
        }
        return dataRefreshLogRepository
                .findFirstByLeague_CodeAndRefreshDateAndRefreshStatusOrderByStartedAtDesc(
                        league.getCode(),
                        latestRefresh.getRefreshDate(),
                        RefreshStatus.SUCCESS
                )
                .orElse(latestRefresh);
    }

    private String dataCoverageStatus(
            League league,
            long finishedMatches,
            long scheduledMatches,
            long activeSourceTargets
    ) {
        if (!league.isActive()) {
            return "INACTIVE";
        }
        if (activeSourceTargets == 0) {
            return "NO_ACTIVE_SOURCES";
        }

        boolean lowHistory = finishedMatches < MINIMUM_FINISHED_MATCHES_FOR_COVERAGE;
        boolean lowFixtures = scheduledMatches > 0
                && scheduledMatches < MINIMUM_SCHEDULED_MATCHES_FOR_FIXTURE_COVERAGE;
        if (lowHistory && lowFixtures) {
            return "LOW_HISTORY_AND_FIXTURES";
        }
        if (lowHistory) {
            return "LOW_HISTORY";
        }
        if (lowFixtures) {
            return "LOW_FIXTURE_COVERAGE";
        }
        return "OK";
    }

    private String dataCoverageMessage(
            League league,
            long finishedMatches,
            long scheduledMatches,
            long activeSourceTargets,
            String coverageStatus
    ) {
        return switch (coverageStatus) {
            case "INACTIVE" -> "League is inactive.";
            case "NO_ACTIVE_SOURCES" -> "No active sources are configured for this league.";
            case "LOW_HISTORY_AND_FIXTURES" -> "Only " + finishedMatches + " finished match(es) and "
                    + scheduledMatches + " scheduled fixture(s) are imported. Calibration needs at least "
                    + MINIMUM_FINISHED_MATCHES_FOR_COVERAGE
                    + " finished matches, and fixture coverage should be verified against TheSportsDB.";
            case "LOW_HISTORY" -> "Only " + finishedMatches
                    + " finished match(es) are imported. Calibration needs at least "
                    + MINIMUM_FINISHED_MATCHES_FOR_COVERAGE + " finished matches.";
            case "LOW_FIXTURE_COVERAGE" -> "Only " + scheduledMatches
                    + " scheduled fixture(s) are imported. Verify fixture coverage against TheSportsDB.";
            default -> "Coverage checks passed using " + activeSourceTargets
                    + " active source target(s) for " + league.getCurrentSeason() + ".";
        };
    }

    private int requiredSeasonCount(CompetitionHistoryPolicy historyPolicy) {
        return historyPolicy == CompetitionHistoryPolicy.INTERNATIONAL_FOUR_YEAR_WINDOW
                ? CompetitionHistoryPolicyService.INTERNATIONAL_HISTORY_WINDOW_YEARS
                : REQUIRED_DOMESTIC_SEASONS;
    }

    private String historyStatus(
            CompetitionHistoryPolicy historyPolicy,
            int requiredSeasonCount,
            List<DashboardLeagueSeasonStatusResponse> seasonBreakdowns,
            long activeSourceTargets
    ) {
        if (activeSourceTargets == 0) {
            return "FAILED";
        }
        if (seasonBreakdowns.isEmpty()) {
            return "PENDING";
        }
        if (historyPolicy == CompetitionHistoryPolicy.INTERNATIONAL_FOUR_YEAR_WINDOW) {
            long finished = seasonBreakdowns.stream().mapToLong(DashboardLeagueSeasonStatusResponse::finishedMatches).sum();
            return finished > 0 ? "COMPLETE" : "PARTIAL";
        }
        return seasonBreakdowns.size() >= requiredSeasonCount ? "COMPLETE" : "PARTIAL";
    }

    private DashboardSourceHealthResponse sourceHealth(SourceTarget sourceTarget) {
        return new DashboardSourceHealthResponse(
                sourceTarget.getId(),
                sourceTarget.getLeague().getCode().name(),
                sourceTarget.getSourceType().name(),
                sourceTarget.getName(),
                sourceTarget.getTargetSeasonLabel(),
                sourceTarget.isActive(),
                sourceTarget.isRobotsTxtRequired(),
                sourceTarget.getRateLimitPerMinute(),
                sourceTarget.getTimeoutMs(),
                sourceTarget.getReliabilityScore(),
                sourceTarget.getFallbackPriority(),
                sourceTarget.isSystemDisabled(),
                sourceTarget.getQuarantinedUntil(),
                sourceTarget.getHealthNote(),
                sourceTarget.getConsecutiveFailures(),
                sourceTarget.getLastSuccessAt(),
                sourceTarget.getLastFailureAt(),
                sourceTarget.getLastFailureReason()
        );
    }

    private boolean shouldShowSourceHealth(SourceTarget sourceTarget) {
        return sourceTarget.isActive();
    }

    private List<DashboardRunSummaryResponse> recentRuns(List<PipelineRun> recentPipelineRuns) {
        List<DashboardRunSummaryResponse> runs = new ArrayList<>();
        dataRefreshLogRepository.findTop10ByOrderByStartedAtDesc().stream()
                .map(this::refreshRun)
                .forEach(runs::add);
        extractionRunRepository.findTop10ByOrderByStartedAtDesc().stream()
                .map(this::extractionRun)
                .forEach(runs::add);
        oddsExtractionRunRepository.findTop10ByOrderByStartedAtDesc().stream()
                .map(this::oddsExtractionRun)
                .forEach(runs::add);
        featureGenerationRunRepository.findTop10ByOrderByStartedAtDesc().stream()
                .map(this::featureRun)
                .forEach(runs::add);
        predictionGenerationRunRepository.findTop10ByOrderByStartedAtDesc().stream()
                .map(this::predictionRun)
                .forEach(runs::add);
        settlementRunRepository.findTop10ByOrderByStartedAtDesc().stream()
                .map(this::settlementRun)
                .forEach(runs::add);
        recentPipelineRuns.stream()
                .map(this::pipelineRun)
                .forEach(runs::add);
        backtestRunRepository.findTop10ByOrderByStartedAtDesc().stream()
                .map(this::backtestRun)
                .forEach(runs::add);
        automationRunRepository.findTop10ByOrderByStartedAtDesc().stream()
                .map(this::automationRun)
                .forEach(runs::add);

        return runs.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(DashboardRunSummaryResponse::startedAt).reversed())
                .limit(RECENT_RUN_LIMIT)
                .toList();
    }

    private record LatestRefreshSummary(
            String status,
            OffsetDateTime startedAt,
            OffsetDateTime finishedAt,
            Long durationMs,
            String failureReason
    ) {
    }

    private DashboardRunSummaryResponse refreshRun(DataRefreshLog run) {
        return new DashboardRunSummaryResponse(
                "REFRESH",
                run.getId(),
                run.getLeague().getCode().name(),
                run.getRefreshStatus().name(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getDurationMs(),
                "sources=" + run.getSourceCount()
                        + ", ingested=" + nullSafe(run.getRecordsIngested())
                        + ", rejected=" + nullSafe(run.getRecordsRejected()),
                run.getFailureReason(), null
        );
    }

    private DashboardRunSummaryResponse extractionRun(ExtractionRun run) {
        return new DashboardRunSummaryResponse(
                "EXTRACTION",
                run.getId(),
                run.getRawSnapshot().getLeague().getCode().name(),
                run.getExtractionStatus().name(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getDurationMs(),
                "rows=" + run.getRowsAccepted() + "/" + run.getRowsSeen()
                        + ", teams=" + run.getTeamsUpserted()
                        + ", matches=" + run.getMatchesUpserted()
                        + ", stats=" + run.getStatsUpserted(),
                run.getFailureReason(), null
        );
    }

    private DashboardRunSummaryResponse oddsExtractionRun(OddsExtractionRun run) {
        return new DashboardRunSummaryResponse(
                "ODDS_EXTRACTION",
                run.getId(),
                run.getRawSnapshot().getLeague().getCode().name(),
                run.getExtractionStatus().name(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getDurationMs(),
                "rows=" + run.getRowsAccepted() + "/" + run.getRowsSeen()
                        + ", snapshots=" + run.getSnapshotsImported()
                        + ", selectionsUpdated=" + run.getSelectionsUpdated()
                        + ", errors=" + run.getValidationErrorCount(),
                run.getFailureReason(), null
        );
    }

    private DashboardRunSummaryResponse featureRun(FeatureGenerationRun run) {
        return new DashboardRunSummaryResponse(
                "FEATURES",
                run.getId(),
                run.getLeague().getCode().name(),
                run.getFeatureStatus().name(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getDurationMs(),
                "matches=" + run.getMatchesSampled()
                        + ", teamFeatures=" + run.getTeamFeaturesGenerated()
                        + ", leagueBaselines=" + run.getLeagueBaselinesGenerated(),
                run.getFailureReason(), null
        );
    }

    private DashboardRunSummaryResponse predictionRun(PredictionGenerationRun run) {
        return new DashboardRunSummaryResponse(
                "PREDICTIONS",
                run.getId(),
                run.getLeague().getCode().name(),
                run.getGenerationStatus().name(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getDurationMs(),
                "model=" + run.getModelVersion()
                        + ", matches=" + run.getMatchesEvaluated()
                        + ", generated=" + run.getSelectionsGenerated()
                        + ", skipped=" + run.getSelectionsSkipped(),
                run.getFailureReason(), null
        );
    }

    private DashboardRunSummaryResponse settlementRun(SettlementRun run) {
        return new DashboardRunSummaryResponse(
                "SETTLEMENT",
                run.getId(),
                run.getLeague().getCode().name(),
                run.getSettlementStatus().name(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getDurationMs(),
                "model=" + run.getModelVersion()
                        + ", evaluated=" + run.getSelectionsEvaluated()
                        + ", won=" + run.getWonCount()
                        + ", lost=" + run.getLostCount()
                        + ", void=" + run.getVoidCount()
                        + ", skipped=" + run.getSkippedCount(),
                run.getFailureReason(), null
        );
    }

    private DashboardRunSummaryResponse pipelineRun(PipelineRun run) {
        return new DashboardRunSummaryResponse(
                "PIPELINE",
                run.getId(),
                run.getLeagueCodes(),
                run.getPipelineStatus().name(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getDurationMs(),
                truncate(run.getStepSummaryJson(), 180),
                run.getFailureReason(), null
        );
    }

    private DashboardRunSummaryResponse backtestRun(BacktestRun run) {
        return new DashboardRunSummaryResponse(
                "BACKTEST",
                run.getId(),
                run.getLeagueCodes(),
                run.getStatus().name(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getDurationMs(),
                "model=" + run.getModelVersion()
                        + ", selections=" + run.getTotalSelections()
                        + ", priced=" + run.getTotalPriced()
                        + ", roi=" + run.getRealizedRoi(),
                run.getSummary(),
                null
        );
    }

    private DashboardRunSummaryResponse automationRun(AutomationRun run) {
        return new DashboardRunSummaryResponse(
                "AUTOMATION",
                run.getId(),
                run.getLeagueCodes(),
                run.getRunStatus().name(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getDurationMs(),
                "model=" + run.getModelVersion()
                        + ", attempts=" + run.getAttemptCount()
                        + ", warnings=" + run.getWarningCount(),
                run.getFailureReason(),
                run.getAttemptCount()
        );
    }

    private List<String> alerts(
            DashboardTotalsResponse totals,
            List<DashboardLeagueStatusResponse> leagues,
            List<DashboardSourceHealthResponse> sources,
            List<DashboardRunSummaryResponse> recentRuns
    ) {
        List<String> alerts = new ArrayList<>();
        if (totals.activeSourceTargets() == 0) {
            alerts.add("No active source targets are registered. TheSportsDB and The Odds API refreshes cannot import new data.");
        }
        long recentFailedAutomations = automationRunRepository.findAll().stream()
                .filter(run -> run.getRunStatus() == AutomationRunStatus.FAILED)
                .filter(run -> run.getStartedAt().isAfter(OffsetDateTime.now(clock).minusHours(24)))
                .count();
        if (recentFailedAutomations > 0) {
            alerts.add(recentFailedAutomations + " automation run(s) have failed in the last 24 hours. Check Recent Runs.");
        }
        sources.stream()
                .filter(DashboardSourceHealthResponse::systemDisabled)
                .forEach(source -> alerts.add("Source " + source.name() + " is system disabled."));
        sources.stream()
                .filter(source -> source.active() && source.quarantinedUntil() != null && source.quarantinedUntil().isAfter(OffsetDateTime.now(clock)))
                .forEach(source -> alerts.add("Source " + source.name()
                        + " is temporarily quarantined until " + source.quarantinedUntil() + "."));
        sources.stream()
                .filter(SourceTarget -> SourceTarget.active() && SourceTarget.consecutiveFailures() >= SOURCE_FAILURE_ALERT_THRESHOLD)
                .forEach(source -> alerts.add("Source " + source.name()
                        + " has " + source.consecutiveFailures() + " consecutive failures."));
        leagues.stream()
                .filter(league -> "FAILED".equals(league.latestRefreshStatus()))
                .forEach(league -> alerts.add("Latest refresh failed for " + league.leagueCode()
                        + ": " + league.latestRefreshFailureReason()));
        List<DashboardLeagueStatusResponse> lowHistoryLeagues = leagues.stream()
                .filter(league -> "LOW_HISTORY".equals(league.dataCoverageStatus())
                        || "LOW_HISTORY_AND_FIXTURES".equals(league.dataCoverageStatus()))
                .toList();
        if (!lowHistoryLeagues.isEmpty()) {
            alerts.add(lowHistoryLeagues.size() + " active league(s) have too little imported history for reliable calibration: "
                    + leagueExamples(lowHistoryLeagues) + ".");
        }
        List<DashboardLeagueStatusResponse> lowFixtureLeagues = leagues.stream()
                .filter(league -> "LOW_FIXTURE_COVERAGE".equals(league.dataCoverageStatus())
                        || "LOW_HISTORY_AND_FIXTURES".equals(league.dataCoverageStatus()))
                .toList();
        if (!lowFixtureLeagues.isEmpty()) {
            alerts.add(lowFixtureLeagues.size() + " active league(s) have very low scheduled fixture coverage: "
                    + leagueExamples(lowFixtureLeagues) + ".");
        }
        recentRuns.stream()
                .filter(run -> ("FAILED".equals(run.status()) || "PARTIAL_SUCCESS".equals(run.status())) && run.startedAt().isAfter(OffsetDateTime.now(clock).minusHours(24)))
                .filter(run -> !duplicateSuccessRefreshFailure(run.failureReason()))
                .limit(5)
                .forEach(run -> {
                    String reason = run.failureReason() != null ? run.failureReason() : (run.summary() != null ? run.summary() : "Check run details");
                    alerts.add(run.stage() + " run failed/partial for " + run.leagueCode() + " in the last 24h: " + reason);
                });
        return alerts.stream().distinct().toList();
    }

    private boolean duplicateSuccessRefreshFailure(DataRefreshLog log) {
        return log != null
                && log.getRefreshStatus() == RefreshStatus.FAILED
                && duplicateSuccessRefreshFailure(log.getFailureReason());
    }

    private boolean duplicateSuccessRefreshFailure(String failureReason) {
        return failureReason != null && failureReason.contains(DUPLICATE_SUCCESS_REFRESH_CONSTRAINT);
    }

    private String dashboardStatus(
            List<String> alerts,
            List<DashboardSourceHealthResponse> sources,
            List<DashboardRunSummaryResponse> recentRuns
    ) {
        boolean criticalSourceFailure = sources.stream()
                .anyMatch(source -> source.active() && source.consecutiveFailures() >= SOURCE_FAILURE_ALERT_THRESHOLD);
        boolean runningCoreJob = recentRuns.stream()
                .anyMatch(run -> "RUNNING".equals(run.status()));
        if (criticalSourceFailure) {
            return "CRITICAL";
        }
        if (!alerts.isEmpty()) {
            return "DEGRADED";
        }
        if (runningCoreJob) {
            return "RUNNING";
        }
        return "OK";
    }

    private String leagueExamples(List<DashboardLeagueStatusResponse> leagues) {
        String examples = leagues.stream()
                .limit(5)
                .map(DashboardLeagueStatusResponse::leagueCode)
                .reduce((left, right) -> left + ", " + right)
                .orElse("none");
        if (leagues.size() > 5) {
            return examples + " and " + (leagues.size() - 5) + " more";
        }
        return examples;
    }

    private long nullSafe(Long value) {
        return value == null ? 0L : value;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
