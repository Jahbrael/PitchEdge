package com.betai.service;

import com.betai.api.dto.DailyFeatureGenerationRequest;
import com.betai.api.dto.DailyFeatureGenerationResponse;
import com.betai.api.dto.FeatureGenerationRunResponse;
import com.betai.api.dto.LeagueBaselineResponse;
import com.betai.api.dto.TeamFeatureSnapshotResponse;
import com.betai.domain.feature.FeatureGenerationRun;
import com.betai.domain.feature.FeatureGenerationStatus;
import com.betai.domain.feature.FeatureGroup;
import com.betai.domain.feature.LeagueBaseline;
import com.betai.domain.feature.SeasonSelectionMode;
import com.betai.domain.feature.TeamFeatureSnapshot;
import com.betai.domain.league.CompetitionHistoryPolicy;
import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.match.Match;
import com.betai.domain.statistics.MatchStatistics;
import com.betai.domain.team.Team;
import com.betai.exception.ReferenceDataNotFoundException;
import com.betai.exception.ResourceNotFoundException;
import com.betai.repository.FeatureGenerationRunRepository;
import com.betai.repository.LeagueBaselineRepository;
import com.betai.repository.LeagueRepository;
import com.betai.repository.MatchRepository;
import com.betai.repository.TeamFeatureSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FeatureEngineeringServiceImpl implements FeatureEngineeringService {

    private static final int AVERAGE_SCALE = 4;
    private static final int RATE_SCALE = 6;

    private final LeagueRepository leagueRepository;
    private final MatchRepository matchRepository;
    private final LeagueBaselineRepository leagueBaselineRepository;
    private final TeamFeatureSnapshotRepository teamFeatureSnapshotRepository;
    private final FeatureGenerationRunRepository featureGenerationRunRepository;
    private final HistoricalSeasonWindowService historicalSeasonWindowService;
    private final CompetitionHistoryPolicyService competitionHistoryPolicyService;
    private final Clock clock;

    @Override
    @Transactional
    public DailyFeatureGenerationResponse generateFeatures(DailyFeatureGenerationRequest request) {
        OffsetDateTime triggeredAt = OffsetDateTime.now(clock);
        LocalDate calculationDate = request.calculationDate() == null ? LocalDate.now(clock) : request.calculationDate();
        List<League> leagues = resolveLeagues(request.leagueCodes());
        List<FeatureGenerationRunResponse> responses = new ArrayList<>();

        for (League league : leagues) {
            responses.add(generateLeagueFeatures(
                    league,
                    calculationDate,
                    request.forceRegenerate(),
                    request.requestedSeasonCount(),
                    request.seasonSelectionMode(),
                    request.customSeasonIds()
            ));
        }

        return new DailyFeatureGenerationResponse(UUID.randomUUID(), triggeredAt, List.copyOf(responses));
    }

    @Override
    @Transactional(readOnly = true)
    public LeagueBaselineResponse getLeagueBaseline(LeagueCode leagueCode, LocalDate calculationDate) {
        League league = leagueRepository.findByCode(leagueCode)
                .orElseThrow(() -> new ResourceNotFoundException("League not found: " + leagueCode + "."));
        HistoricalSeasonWindow window = historicalSeasonWindowService.resolveWindow(
                league,
                calculationDate,
                null,
                SeasonSelectionMode.CURRENT_AND_RECENT_COMPLETE,
                null,
                FeatureGroup.RESULTS
        );
        return leagueBaselineRepository
                .findByLeague_CodeAndCalculationDateAndSeasonWindowKey(leagueCode, calculationDate, window.seasonWindowKey())
                .or(() -> leagueBaselineRepository.findByLeague_CodeAndSeasonLabelAndCalculationDate(leagueCode, league.getCurrentSeason(), calculationDate))
                .map(LeagueBaselineResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("League baseline not found for "
                        + leagueCode + " on " + calculationDate + "."));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeamFeatureSnapshotResponse> listTeamFeatures(LeagueCode leagueCode, LocalDate calculationDate) {
        League league = leagueRepository.findByCode(leagueCode)
                .orElseThrow(() -> new ResourceNotFoundException("League not found: " + leagueCode + "."));
        HistoricalSeasonWindow window = historicalSeasonWindowService.resolveWindow(
                league,
                calculationDate,
                null,
                SeasonSelectionMode.CURRENT_AND_RECENT_COMPLETE,
                null,
                FeatureGroup.RESULTS
        );
        return teamFeatureSnapshotRepository
                .findByLeague_CodeAndCalculationDateAndSeasonWindowKeyOrderByTeam_CanonicalNameAsc(
                        leagueCode,
                        calculationDate,
                        window.seasonWindowKey())
                .stream()
                .map(TeamFeatureSnapshotResponse::from)
                .toList();
    }

    private FeatureGenerationRunResponse generateLeagueFeatures(
            League league,
            LocalDate calculationDate,
            boolean forceRegenerate,
            Integer requestedSeasonCount,
            SeasonSelectionMode seasonSelectionMode,
            Set<String> customSeasonIds
    ) {
        HistoricalSeasonWindow window = historicalSeasonWindowService.resolveWindow(
                league,
                calculationDate,
                requestedSeasonCount,
                seasonSelectionMode,
                customSeasonIds,
                FeatureGroup.RESULTS
        );
        if (!forceRegenerate) {
            var cached = featureGenerationRunRepository
                    .findFirstByLeague_CodeAndCalculationDateAndSeasonWindowKeyAndFeatureStatusOrderByStartedAtDesc(
                            league.getCode(),
                            calculationDate,
                            window.seasonWindowKey(),
                            FeatureGenerationStatus.SUCCESS
                    );
            if (cached.isPresent()) {
                return FeatureGenerationRunResponse.from(cached.get(), true);
            }
        }

        FeatureGenerationRun run = featureGenerationRunRepository.save(new FeatureGenerationRun()
                .setLeague(league)
                .setCalculationDate(calculationDate)
                .setSeasonLabel(league.getCurrentSeason())
                .setRequestedSeasonCount(window.requestedSeasonCount())
                .setActualSeasonCountUsed(window.actualSeasonCountUsed())
                .setSeasonSelectionMode(window.seasonSelectionMode().name())
                .setSelectedSeasonIds(String.join(",", window.selectedSeasonIds()))
                .setSeasonWindowKey(window.seasonWindowKey())
                .setFallbackApplied(window.fallbackApplied())
                .setFeatureStatus(FeatureGenerationStatus.RUNNING)
                .setStartedAt(OffsetDateTime.now(clock)));

        try {
            if (window.actualSeasonCountUsed() == 0) {
                run.finish(
                        OffsetDateTime.now(clock),
                        FeatureGenerationStatus.SKIPPED,
                        0,
                        0,
                        0,
                        "No usable historical seasons are available for feature generation."
                );
                return FeatureGenerationRunResponse.from(featureGenerationRunRepository.save(run), false);
            }
            List<Match> matches = matchesForFeatureGeneration(league, window, calculationDate);

            if (matches.isEmpty()) {
                run.finish(
                        OffsetDateTime.now(clock),
                        FeatureGenerationStatus.SKIPPED,
                        0,
                        0,
                        0,
                        "No finished matches are available for feature generation."
                );
                return FeatureGenerationRunResponse.from(featureGenerationRunRepository.save(run), false);
            }

            Map<String, BigDecimal> weightsBySeason = weightsBySeason(window);
            LeagueBaseline baseline = upsertLeagueBaseline(league, calculationDate, matches, window, weightsBySeason);
            int teamFeatureCount = upsertTeamFeatures(league, calculationDate, matches, window, weightsBySeason);

            run.finish(
                    OffsetDateTime.now(clock),
                    FeatureGenerationStatus.SUCCESS,
                    matches.size(),
                    teamFeatureCount,
                    baseline == null ? 0 : 1,
                    null
            );
            return FeatureGenerationRunResponse.from(featureGenerationRunRepository.save(run), false);
        } catch (Exception exception) {
            run.finish(
                    OffsetDateTime.now(clock),
                    FeatureGenerationStatus.FAILED,
                    run.getMatchesSampled(),
                    run.getTeamFeaturesGenerated(),
                    run.getLeagueBaselinesGenerated(),
                    truncate(exception.getMessage(), 1000)
            );
            return FeatureGenerationRunResponse.from(featureGenerationRunRepository.save(run), false);
        }
    }

    private List<Match> matchesForFeatureGeneration(
            League league,
            HistoricalSeasonWindow window,
            LocalDate calculationDate
    ) {
        if (isWorldCupRollingWindow(league, window)) {
            LocalDate fromDate = window.oldestDataDate() == null
                    ? calculationDate.minusYears(Math.max(1, window.requestedSeasonCount()))
                    : window.oldestDataDate();
            LocalDate toDate = window.newestDataDate() == null ? calculationDate : window.newestDataDate();
            return matchRepository.findFinishedMatchesForFeatureGenerationDateWindow(
                    league.getCode(),
                    fromDate,
                    toDate
            );
        }
        return matchRepository.findFinishedMatchesForFeatureGenerationWindow(
                league.getCode(),
                window.selectedSeasonIds(),
                calculationDate
        );
    }

    private boolean isWorldCupRollingWindow(League league, HistoricalSeasonWindow window) {
        return competitionHistoryPolicyService.policyFor(league) == CompetitionHistoryPolicy.INTERNATIONAL_FOUR_YEAR_WINDOW
                && window.seasonWindowKey() != null
                && window.seasonWindowKey().contains("ROLLING_YEARS");
    }

    private LeagueBaseline upsertLeagueBaseline(
            League league,
            LocalDate calculationDate,
            List<Match> matches,
            HistoricalSeasonWindow window,
            Map<String, BigDecimal> weightsBySeason
    ) {
        LeagueBaseline baseline = leagueBaselineRepository
                .findByLeague_CodeAndCalculationDateAndSeasonWindowKey(
                        league.getCode(),
                        calculationDate,
                        window.seasonWindowKey()
                )
                .orElseGet(LeagueBaseline::new);

        CountedAverage corners = new CountedAverage();
        CountedAverage yellowCards = new CountedAverage();
        CountedRate redCards = new CountedRate();

        BigDecimal homeGoals = BigDecimal.ZERO;
        BigDecimal awayGoals = BigDecimal.ZERO;
        BigDecimal homeWins = BigDecimal.ZERO;
        BigDecimal draws = BigDecimal.ZERO;
        BigDecimal awayWins = BigDecimal.ZERO;
        BigDecimal btts = BigDecimal.ZERO;
        BigDecimal over15 = BigDecimal.ZERO;
        BigDecimal over25 = BigDecimal.ZERO;
        BigDecimal under35 = BigDecimal.ZERO;
        BigDecimal sampleWeight = BigDecimal.ZERO;

        for (Match match : matches) {
            BigDecimal seasonWeight = weightFor(match, weightsBySeason);
            int homeScore = match.getHomeScore();
            int awayScore = match.getAwayScore();
            int totalGoals = homeScore + awayScore;

            sampleWeight = sampleWeight.add(seasonWeight);
            homeGoals = homeGoals.add(seasonWeight.multiply(BigDecimal.valueOf(homeScore)));
            awayGoals = awayGoals.add(seasonWeight.multiply(BigDecimal.valueOf(awayScore)));
            homeWins = homeWins.add(homeScore > awayScore ? seasonWeight : BigDecimal.ZERO);
            draws = draws.add(homeScore == awayScore ? seasonWeight : BigDecimal.ZERO);
            awayWins = awayWins.add(awayScore > homeScore ? seasonWeight : BigDecimal.ZERO);
            btts = btts.add(homeScore > 0 && awayScore > 0 ? seasonWeight : BigDecimal.ZERO);
            over15 = over15.add(totalGoals > 1 ? seasonWeight : BigDecimal.ZERO);
            over25 = over25.add(totalGoals > 2 ? seasonWeight : BigDecimal.ZERO);
            under35 = under35.add(totalGoals < 4 ? seasonWeight : BigDecimal.ZERO);

            MatchStatistics stats = match.getStatistics();
            if (stats != null) {
                corners.addIfBothPresent(stats.getHomeCorners(), stats.getAwayCorners(), seasonWeight);
                yellowCards.addIfBothPresent(stats.getHomeYellowCards(), stats.getAwayYellowCards(), seasonWeight);
                redCards.addIfBothPresent(stats.getHomeRedCards(), stats.getAwayRedCards(), seasonWeight);
            }
        }

        int sample = matches.size();
        LeagueBaseline prepared = baseline
                .setLeague(league)
                .setSeasonLabel(window.selectedSeasonIds().isEmpty()
                        ? league.getCurrentSeason()
                        : window.selectedSeasonIds().getFirst())
                .setCalculationDate(calculationDate)
                .setMatchesSampled(sample)
                .setAvgHomeGoals(weighted(homeGoals, sampleWeight, AVERAGE_SCALE))
                .setAvgAwayGoals(weighted(awayGoals, sampleWeight, AVERAGE_SCALE))
                .setAvgTotalGoals(weighted(homeGoals.add(awayGoals), sampleWeight, AVERAGE_SCALE))
                .setHomeWinRate(weighted(homeWins, sampleWeight, RATE_SCALE))
                .setDrawRate(weighted(draws, sampleWeight, RATE_SCALE))
                .setAwayWinRate(weighted(awayWins, sampleWeight, RATE_SCALE))
                .setBttsRate(weighted(btts, sampleWeight, RATE_SCALE))
                .setOver15Rate(weighted(over15, sampleWeight, RATE_SCALE))
                .setOver25Rate(weighted(over25, sampleWeight, RATE_SCALE))
                .setUnder35Rate(weighted(under35, sampleWeight, RATE_SCALE))
                .setAvgTotalCorners(corners.averageOrNull())
                .setAvgTotalYellowCards(yellowCards.averageOrNull())
                .setRedCardRate(redCards.rateOrNull());
        applyWindowMetadata(prepared, window);
        return leagueBaselineRepository.save(prepared);
    }

    private int upsertTeamFeatures(
            League league,
            LocalDate calculationDate,
            List<Match> matches,
            HistoricalSeasonWindow window,
            Map<String, BigDecimal> weightsBySeason
    ) {
        Map<UUID, TeamFacts> factsByTeam = new HashMap<>();
        for (Match match : matches) {
            BigDecimal seasonWeight = weightFor(match, weightsBySeason);
            addMatchFacts(factsByTeam, match, true, seasonWeight);
            addMatchFacts(factsByTeam, match, false, seasonWeight);
        }

        int saved = 0;
        for (TeamFacts facts : factsByTeam.values()) {
            TeamFeatureSnapshot snapshot = teamFeatureSnapshotRepository
                    .findByLeague_CodeAndTeam_IdAndCalculationDateAndSeasonWindowKey(
                            league.getCode(),
                            facts.team().getId(),
                            calculationDate,
                            window.seasonWindowKey()
                    )
                    .orElseGet(TeamFeatureSnapshot::new);

            TeamFeatureValues values = calculateTeamValues(league, facts.facts());
            TeamFeatureSnapshot prepared = snapshot
                    .setLeague(league)
                    .setTeam(facts.team())
                    .setSeasonLabel(window.selectedSeasonIds().isEmpty()
                            ? league.getCurrentSeason()
                            : window.selectedSeasonIds().getFirst())
                    .setCalculationDate(calculationDate)
                    .setMatchesPlayed(values.matchesPlayed())
                    .setHomeMatches(values.homeMatches())
                    .setAwayMatches(values.awayMatches())
                    .setLast5Matches(values.last5Matches())
                    .setLast10Matches(values.last10Matches())
                    .setPointsPerMatch(values.pointsPerMatch())
                    .setLast5PointsPerMatch(values.last5PointsPerMatch())
                    .setLast10PointsPerMatch(values.last10PointsPerMatch())
                    .setGoalsForPerMatch(values.goalsForPerMatch())
                    .setGoalsAgainstPerMatch(values.goalsAgainstPerMatch())
                    .setHomeGoalsForPerMatch(values.homeGoalsForPerMatch())
                    .setHomeGoalsAgainstPerMatch(values.homeGoalsAgainstPerMatch())
                    .setAwayGoalsForPerMatch(values.awayGoalsForPerMatch())
                    .setAwayGoalsAgainstPerMatch(values.awayGoalsAgainstPerMatch())
                    .setCleanSheetRate(values.cleanSheetRate())
                    .setFailedToScoreRate(values.failedToScoreRate())
                    .setBttsRate(values.bttsRate())
                    .setOver15Rate(values.over15Rate())
                    .setOver25Rate(values.over25Rate())
                    .setUnder35Rate(values.under35Rate())
                    .setCornersForPerMatch(values.cornersForPerMatch())
                    .setCornersAgainstPerMatch(values.cornersAgainstPerMatch())
                    .setYellowCardsForPerMatch(values.yellowCardsForPerMatch())
                    .setYellowCardsAgainstPerMatch(values.yellowCardsAgainstPerMatch())
                    .setRedCardRate(values.redCardRate())
                    .setFormScore(values.formScore());
            applyWindowMetadata(prepared, window);
            teamFeatureSnapshotRepository.save(prepared);
            saved++;
        }
        return saved;
    }

    private void addMatchFacts(
            Map<UUID, TeamFacts> factsByTeam,
            Match match,
            boolean homeSide,
            BigDecimal seasonWeight
    ) {
        Team team = homeSide ? match.getHomeTeam() : match.getAwayTeam();
        MatchStatistics stats = match.getStatistics();
        int goalsFor = homeSide ? match.getHomeScore() : match.getAwayScore();
        int goalsAgainst = homeSide ? match.getAwayScore() : match.getHomeScore();
        int points = goalsFor > goalsAgainst ? 3 : goalsFor == goalsAgainst ? 1 : 0;

        TeamMatchFact fact = new TeamMatchFact(
                match.getKickoffAt(),
                homeSide,
                goalsFor,
                goalsAgainst,
                points,
                nullableStat(stats, homeSide, StatSide.CORNERS_FOR),
                nullableStat(stats, homeSide, StatSide.CORNERS_AGAINST),
                nullableStat(stats, homeSide, StatSide.YELLOW_FOR),
                nullableStat(stats, homeSide, StatSide.YELLOW_AGAINST),
                nullableStat(stats, homeSide, StatSide.RED_FOR),
                seasonWeight
        );

        factsByTeam.computeIfAbsent(team.getId(), ignored -> new TeamFacts(team, new ArrayList<>()))
                .facts()
                .add(fact);
    }

    private TeamFeatureValues calculateTeamValues(League league, List<TeamMatchFact> facts) {
        facts.sort(Comparator.comparing(TeamMatchFact::kickoffAt));
        List<TeamMatchFact> last5 = last(facts, 5);
        List<TeamMatchFact> last10 = last(facts, 10);

        int matches = facts.size();
        int homeMatches = count(facts, TeamMatchFact::home);
        int awayMatches = matches - homeMatches;

        BigDecimal pointsPerMatch = weightedAverage(facts, TeamMatchFact::points);
        BigDecimal last5Ppm = avg(last5.stream().mapToInt(TeamMatchFact::points).sum(), last5.size());
        BigDecimal last10Ppm = avg(last10.stream().mapToInt(TeamMatchFact::points).sum(), last10.size());
        BigDecimal goalsForPerMatch = weightedAverage(facts, TeamMatchFact::goalsFor);
        BigDecimal goalsAgainstPerMatch = weightedAverage(facts, TeamMatchFact::goalsAgainst);
        BigDecimal homeGoalsForPerMatch = goalsAverage(facts, true, true);
        BigDecimal homeGoalsAgainstPerMatch = goalsAverage(facts, true, false);
        BigDecimal awayGoalsForPerMatch = goalsAverage(facts, false, true);
        BigDecimal awayGoalsAgainstPerMatch = goalsAverage(facts, false, false);
        if (neutralSiteLeague(league.getCode())) {
            homeGoalsForPerMatch = goalsForPerMatch;
            homeGoalsAgainstPerMatch = goalsAgainstPerMatch;
            awayGoalsForPerMatch = goalsForPerMatch;
            awayGoalsAgainstPerMatch = goalsAgainstPerMatch;
        }
        BigDecimal formScore = pointsPerMatch
                .multiply(new BigDecimal("0.30"))
                .add(last5Ppm.multiply(new BigDecimal("0.50")))
                .add(last10Ppm.multiply(new BigDecimal("0.20")))
                .setScale(AVERAGE_SCALE, RoundingMode.HALF_UP);

        return new TeamFeatureValues(
                matches,
                homeMatches,
                awayMatches,
                last5.size(),
                last10.size(),
                pointsPerMatch,
                last5Ppm,
                last10Ppm,
                goalsForPerMatch,
                goalsAgainstPerMatch,
                homeGoalsForPerMatch,
                homeGoalsAgainstPerMatch,
                awayGoalsForPerMatch,
                awayGoalsAgainstPerMatch,
                weightedRate(facts, fact -> fact.goalsAgainst() == 0),
                weightedRate(facts, fact -> fact.goalsFor() == 0),
                weightedRate(facts, fact -> fact.goalsFor() > 0 && fact.goalsAgainst() > 0),
                weightedRate(facts, fact -> fact.goalsFor() + fact.goalsAgainst() > 1),
                weightedRate(facts, fact -> fact.goalsFor() + fact.goalsAgainst() > 2),
                weightedRate(facts, fact -> fact.goalsFor() + fact.goalsAgainst() < 4),
                nullableWeightedAverage(facts, TeamMatchFact::cornersFor),
                nullableWeightedAverage(facts, TeamMatchFact::cornersAgainst),
                nullableWeightedAverage(facts, TeamMatchFact::yellowCardsFor),
                nullableWeightedAverage(facts, TeamMatchFact::yellowCardsAgainst),
                nullableWeightedRate(facts, TeamMatchFact::redCardsFor),
                formScore
        );
    }

    private boolean neutralSiteLeague(LeagueCode leagueCode) {
        return competitionHistoryPolicyService.policyFor(leagueCode) == CompetitionHistoryPolicy.INTERNATIONAL_FOUR_YEAR_WINDOW;
    }

    private BigDecimal goalsAverage(List<TeamMatchFact> facts, boolean home, boolean forGoals) {
        List<TeamMatchFact> filtered = facts.stream().filter(fact -> fact.home() == home).toList();
        if (filtered.isEmpty()) {
            return null;
        }
        return weightedAverage(filtered, forGoals ? TeamMatchFact::goalsFor : TeamMatchFact::goalsAgainst);
    }

    private Map<String, BigDecimal> weightsBySeason(HistoricalSeasonWindow window) {
        Map<String, BigDecimal> weightsBySeason = new HashMap<>();
        List<String> seasons = window.selectedSeasonIds();
        List<BigDecimal> weights = window.recencyWeights();
        for (int index = 0; index < seasons.size(); index++) {
            BigDecimal weight = index < weights.size() ? weights.get(index) : BigDecimal.ONE;
            weightsBySeason.put(seasons.get(index), weight);
        }
        return weightsBySeason;
    }

    private BigDecimal weightFor(Match match, Map<String, BigDecimal> weightsBySeason) {
        return weightsBySeason.getOrDefault(match.getSeasonLabel(), BigDecimal.ONE);
    }

    private BigDecimal weightedAverage(List<TeamMatchFact> facts, WeightedValueExtractor extractor) {
        if (facts.isEmpty()) {
            return BigDecimal.ZERO.setScale(AVERAGE_SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal weight = BigDecimal.ZERO;
        for (TeamMatchFact fact : facts) {
            total = total.add(fact.seasonWeight().multiply(BigDecimal.valueOf(extractor.value(fact))));
            weight = weight.add(fact.seasonWeight());
        }
        return weighted(total, weight, AVERAGE_SCALE);
    }

    private BigDecimal weightedRate(List<TeamMatchFact> facts, FactPredicate predicate) {
        if (facts.isEmpty()) {
            return BigDecimal.ZERO.setScale(RATE_SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal positive = BigDecimal.ZERO;
        BigDecimal weight = BigDecimal.ZERO;
        for (TeamMatchFact fact : facts) {
            weight = weight.add(fact.seasonWeight());
            if (predicate.test(fact)) {
                positive = positive.add(fact.seasonWeight());
            }
        }
        return weighted(positive, weight, RATE_SCALE);
    }

    private BigDecimal nullableWeightedAverage(List<TeamMatchFact> facts, NullableWeightedValueExtractor extractor) {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal weight = BigDecimal.ZERO;
        for (TeamMatchFact fact : facts) {
            Integer value = extractor.value(fact);
            if (value != null) {
                total = total.add(fact.seasonWeight().multiply(BigDecimal.valueOf(value)));
                weight = weight.add(fact.seasonWeight());
            }
        }
        return weight.signum() == 0 ? null : weighted(total, weight, AVERAGE_SCALE);
    }

    private BigDecimal nullableWeightedRate(List<TeamMatchFact> facts, NullableWeightedValueExtractor extractor) {
        BigDecimal positive = BigDecimal.ZERO;
        BigDecimal weight = BigDecimal.ZERO;
        for (TeamMatchFact fact : facts) {
            Integer value = extractor.value(fact);
            if (value != null) {
                weight = weight.add(fact.seasonWeight());
                if (value > 0) {
                    positive = positive.add(fact.seasonWeight());
                }
            }
        }
        return weight.signum() == 0 ? null : weighted(positive, weight, RATE_SCALE);
    }

    private BigDecimal weighted(BigDecimal total, BigDecimal weight, int scale) {
        if (weight == null || weight.signum() == 0) {
            return BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP);
        }
        return total.divide(weight, scale, RoundingMode.HALF_UP);
    }

    private void applyWindowMetadata(LeagueBaseline baseline, HistoricalSeasonWindow window) {
        baseline
                .setRequestedSeasonCount(window.requestedSeasonCount())
                .setActualSeasonCountUsed(window.actualSeasonCountUsed())
                .setSeasonSelectionMode(window.seasonSelectionMode().name())
                .setSelectedSeasonIds(join(window.selectedSeasonIds()))
                .setSelectedSeasonNames(join(window.selectedSeasonNames()))
                .setCurrentSeasonIncluded(window.currentSeasonIncluded())
                .setFallbackApplied(window.fallbackApplied())
                .setOldestDataDate(window.oldestDataDate())
                .setNewestDataDate(window.newestDataDate())
                .setCompletedMatchesUsed(window.completedMatchesUsed())
                .setMarketSpecificUsableSeasonCount(window.marketSpecificUsableSeasonCount())
                .setRecencyWeightingVersion(window.recencyWeightingVersion())
                .setSeasonWindowKey(window.seasonWindowKey())
                .setHistoricalDepthStatus(window.historicalDepthStatus().name())
                .setMarketSpecificDataCoverage(window.marketSpecificDataCoverage());
    }

    private void applyWindowMetadata(TeamFeatureSnapshot snapshot, HistoricalSeasonWindow window) {
        snapshot
                .setRequestedSeasonCount(window.requestedSeasonCount())
                .setActualSeasonCountUsed(window.actualSeasonCountUsed())
                .setSeasonSelectionMode(window.seasonSelectionMode().name())
                .setSelectedSeasonIds(join(window.selectedSeasonIds()))
                .setSelectedSeasonNames(join(window.selectedSeasonNames()))
                .setCurrentSeasonIncluded(window.currentSeasonIncluded())
                .setFallbackApplied(window.fallbackApplied())
                .setOldestDataDate(window.oldestDataDate())
                .setNewestDataDate(window.newestDataDate())
                .setCompletedMatchesUsed(window.completedMatchesUsed())
                .setMarketSpecificUsableSeasonCount(window.marketSpecificUsableSeasonCount())
                .setRecencyWeightingVersion(window.recencyWeightingVersion())
                .setSeasonWindowKey(window.seasonWindowKey())
                .setHistoricalDepthStatus(window.historicalDepthStatus().name())
                .setMarketSpecificDataCoverage(window.marketSpecificDataCoverage());
    }

    private String join(List<String> values) {
        return values == null || values.isEmpty() ? null : String.join(",", values);
    }

    private Integer nullableStat(MatchStatistics stats, boolean homeSide, StatSide statSide) {
        if (stats == null) {
            return null;
        }
        return switch (statSide) {
            case CORNERS_FOR -> homeSide ? stats.getHomeCorners() : stats.getAwayCorners();
            case CORNERS_AGAINST -> homeSide ? stats.getAwayCorners() : stats.getHomeCorners();
            case YELLOW_FOR -> homeSide ? stats.getHomeYellowCards() : stats.getAwayYellowCards();
            case YELLOW_AGAINST -> homeSide ? stats.getAwayYellowCards() : stats.getHomeYellowCards();
            case RED_FOR -> homeSide ? stats.getHomeRedCards() : stats.getAwayRedCards();
        };
    }

    private List<League> resolveLeagues(Set<LeagueCode> requestedCodes) {
        if (requestedCodes == null || requestedCodes.isEmpty()) {
            List<League> leagues = leagueRepository.findByActiveTrueAndScrapeEnabledTrueOrderByNameAsc();
            if (leagues.isEmpty()) {
                throw new ReferenceDataNotFoundException("No active leagues are configured.");
            }
            return leagues;
        }

        List<League> leagues = leagueRepository.findByCodeInAndActiveTrue(requestedCodes);
        Set<LeagueCode> activeCodes = leagues.stream().map(League::getCode).collect(Collectors.toSet());
        EnumSet<LeagueCode> missing = EnumSet.copyOf(requestedCodes);
        missing.removeAll(activeCodes);
        if (!missing.isEmpty()) {
            throw new ReferenceDataNotFoundException("Unsupported or inactive leagues: " + missing + ".");
        }
        return leagues;
    }

    private List<TeamMatchFact> last(List<TeamMatchFact> facts, int size) {
        int fromIndex = Math.max(0, facts.size() - size);
        return facts.subList(fromIndex, facts.size());
    }

    private int count(List<TeamMatchFact> facts, FactPredicate predicate) {
        int count = 0;
        for (TeamMatchFact fact : facts) {
            if (predicate.test(fact)) {
                count++;
            }
        }
        return count;
    }

    private BigDecimal avg(int numerator, int denominator) {
        if (denominator == 0) {
            return BigDecimal.ZERO.setScale(AVERAGE_SCALE, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), AVERAGE_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal rate(int numerator, int denominator) {
        if (denominator == 0) {
            return BigDecimal.ZERO.setScale(RATE_SCALE, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), RATE_SCALE, RoundingMode.HALF_UP);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private enum StatSide {
        CORNERS_FOR,
        CORNERS_AGAINST,
        YELLOW_FOR,
        YELLOW_AGAINST,
        RED_FOR
    }

    @FunctionalInterface
    private interface FactPredicate {
        boolean test(TeamMatchFact fact);
    }

    @FunctionalInterface
    private interface WeightedValueExtractor {
        int value(TeamMatchFact fact);
    }

    @FunctionalInterface
    private interface NullableWeightedValueExtractor {
        Integer value(TeamMatchFact fact);
    }

    private record TeamFacts(Team team, List<TeamMatchFact> facts) {
    }

    private record TeamMatchFact(
            OffsetDateTime kickoffAt,
            boolean home,
            int goalsFor,
            int goalsAgainst,
            int points,
            Integer cornersFor,
            Integer cornersAgainst,
            Integer yellowCardsFor,
            Integer yellowCardsAgainst,
            Integer redCardsFor,
            BigDecimal seasonWeight
    ) {
    }

    private record TeamFeatureValues(
            int matchesPlayed,
            int homeMatches,
            int awayMatches,
            int last5Matches,
            int last10Matches,
            BigDecimal pointsPerMatch,
            BigDecimal last5PointsPerMatch,
            BigDecimal last10PointsPerMatch,
            BigDecimal goalsForPerMatch,
            BigDecimal goalsAgainstPerMatch,
            BigDecimal homeGoalsForPerMatch,
            BigDecimal homeGoalsAgainstPerMatch,
            BigDecimal awayGoalsForPerMatch,
            BigDecimal awayGoalsAgainstPerMatch,
            BigDecimal cleanSheetRate,
            BigDecimal failedToScoreRate,
            BigDecimal bttsRate,
            BigDecimal over15Rate,
            BigDecimal over25Rate,
            BigDecimal under35Rate,
            BigDecimal cornersForPerMatch,
            BigDecimal cornersAgainstPerMatch,
            BigDecimal yellowCardsForPerMatch,
            BigDecimal yellowCardsAgainstPerMatch,
            BigDecimal redCardRate,
            BigDecimal formScore
    ) {
    }

    private final class CountedAverage {
        private BigDecimal weight = BigDecimal.ZERO;
        private BigDecimal total = BigDecimal.ZERO;

        void addIfBothPresent(Integer left, Integer right, BigDecimal seasonWeight) {
            if (left != null && right != null) {
                weight = weight.add(seasonWeight);
                total = total.add(seasonWeight.multiply(BigDecimal.valueOf(left + right)));
            }
        }

        BigDecimal averageOrNull() {
            return weight.signum() == 0 ? null : weighted(total, weight, AVERAGE_SCALE);
        }
    }

    private final class CountedRate {
        private BigDecimal weight = BigDecimal.ZERO;
        private BigDecimal positive = BigDecimal.ZERO;

        void addIfBothPresent(Integer left, Integer right, BigDecimal seasonWeight) {
            if (left != null && right != null) {
                weight = weight.add(seasonWeight);
                positive = positive.add(left + right > 0 ? seasonWeight : BigDecimal.ZERO);
            }
        }

        BigDecimal rateOrNull() {
            return weight.signum() == 0 ? null : weighted(positive, weight, RATE_SCALE);
        }
    }
}
