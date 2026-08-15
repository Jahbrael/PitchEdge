package com.betai.service;

import com.betai.api.dto.details.FixturePredictionDetailsResponse;
import com.betai.domain.market.MarketDefinition;
import com.betai.domain.match.Match;
import com.betai.domain.match.MatchStatus;
import com.betai.domain.prediction.PredictionSelection;
import com.betai.domain.statistics.MatchStatistics;
import com.betai.exception.ReferenceDataNotFoundException;
import com.betai.repository.MarketDefinitionRepository;
import com.betai.repository.MatchRepository;
import com.betai.repository.PredictionSelectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.betai.api.dto.PredictionBatchResponse;
import com.betai.api.dto.PredictionResponse;
import com.betai.api.dto.PredictionSelectionResponse;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FixturePredictionDetailsServiceImpl implements FixturePredictionDetailsService {

    private final MatchRepository matchRepository;
    private final PredictionSelectionRepository predictionSelectionRepository;
    private final MarketDefinitionRepository marketDefinitionRepository;
    private final PredictionRunCacheService predictionRunCacheService;

    @Override
    @Transactional(readOnly = true)
    public FixturePredictionDetailsResponse getFixtureDetails(UUID matchId, String modelVersion, String recommendedMarketCode) {
        return getFixtureDetails(matchId, modelVersion, recommendedMarketCode, null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public FixturePredictionDetailsResponse getFixtureDetails(UUID matchId, String modelVersion, String recommendedMarketCode, UUID runId, UUID selectionId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ReferenceDataNotFoundException("Match not found"));

        List<PredictionSelection> dbSelections = predictionSelectionRepository
                .findByMatch_IdAndMarketDefinition_EnabledTrueOrderByProbabilityDesc(matchId);
        List<MarketDefinition> allMarkets = marketDefinitionRepository.findByEnabledTrueOrderByDisplayNameAsc();

        Map<String, PredictionSelection> bestDbByMarket = new HashMap<>();
        for (PredictionSelection sel : dbSelections) {
            String code = sel.getMarketDefinition().getCode().name();
            if (!bestDbByMarket.containsKey(code)) {
                bestDbByMarket.put(code, sel);
            } else if (modelVersion != null && modelVersion.equals(sel.getModelVersion())) {
                bestDbByMarket.put(code, sel);
            }
        }

        List<PredictionSelectionResponse> cachedSelections = new ArrayList<>();
        if (runId != null) {
            PredictionResponse runResponse = predictionRunCacheService.get(runId);
            if (runResponse != null) {
                for (PredictionBatchResponse b : runResponse.batches()) {
                    for (PredictionSelectionResponse s : b.selections()) {
                        if (matchId.equals(s.matchId())) {
                            cachedSelections.add(s);
                        }
                    }
                }
            }
        }

        Map<String, PredictionSelectionResponse> cachedByCode = cachedSelections.stream()
                .collect(Collectors.toMap(PredictionSelectionResponse::marketCode, s -> s, (a, b) -> a));

        List<FixturePredictionDetailsResponse.MarketPredictionDto> markets = new ArrayList<>();
        List<FixturePredictionDetailsResponse.UnavailableMarketDto> unavailableMarkets = new ArrayList<>();
        FixturePredictionDetailsResponse.PredictionSummaryDto summary = null;

        PredictionSelectionResponse summaryCached = null;
        if (selectionId != null) {
            summaryCached = cachedSelections.stream().filter(s -> selectionId.equals(s.selectionId())).findFirst().orElse(null);
        }
        if (summaryCached == null && recommendedMarketCode != null) {
            summaryCached = cachedByCode.get(recommendedMarketCode);
        }
        if (summaryCached == null && !cachedSelections.isEmpty()) {
            summaryCached = cachedSelections.getFirst();
        }

        if (summaryCached != null) {
            summary = new FixturePredictionDetailsResponse.PredictionSummaryDto(
                    summaryCached.valueAssessedAt() != null ? summaryCached.valueAssessedAt() : java.time.OffsetDateTime.now(),
                    summaryCached.modelVersion() != null ? summaryCached.modelVersion() : (modelVersion != null ? modelVersion : "v1"),
                    recommendedMarketCode != null ? recommendedMarketCode : summaryCached.marketCode(),
                    summaryCached.actualSeasonCountUsed() != null ? summaryCached.actualSeasonCountUsed() : 0,
                    summaryCached.completedMatchesUsed() != null ? summaryCached.completedMatchesUsed() : 0,
                    "TheSportsDB",
                    summaryCached.confidenceBand() != null ? summaryCached.confidenceBand() : "UNRATED",
                    summaryCached.reason() != null ? summaryCached.reason() : "Calculated by model run " + runId
            );
        } else if (!dbSelections.isEmpty()) {
            PredictionSelection first = dbSelections.getFirst();
            if (recommendedMarketCode != null && bestDbByMarket.containsKey(recommendedMarketCode)) {
                first = bestDbByMarket.get(recommendedMarketCode);
            }
            summary = new FixturePredictionDetailsResponse.PredictionSummaryDto(
                    first.getGeneratedAt() != null ? first.getGeneratedAt() : java.time.OffsetDateTime.now(),
                    first.getModelVersion() != null ? first.getModelVersion() : (modelVersion != null ? modelVersion : "v1"),
                    recommendedMarketCode != null ? recommendedMarketCode : first.getMarketDefinition().getCode().name(),
                    first.getActualSeasonCountUsed() != null ? first.getActualSeasonCountUsed() : 0,
                    first.getCompletedMatchesUsed() != null ? first.getCompletedMatchesUsed() : 0,
                    "TheSportsDB",
                    first.getConfidenceBand() != null ? first.getConfidenceBand().name() : "UNRATED",
                    first.getCalibrationNote() != null ? first.getCalibrationNote() : "Calculated by standard model"
            );
        } else {
            summary = new FixturePredictionDetailsResponse.PredictionSummaryDto(
                    java.time.OffsetDateTime.now(),
                    modelVersion != null ? modelVersion : "v1",
                    recommendedMarketCode != null ? recommendedMarketCode : "--",
                    0,
                    0,
                    "Local Database",
                    "UNRATED",
                    "No calculated model prediction selections found for this fixture."
            );
        }

        for (MarketDefinition market : allMarkets) {
            String code = market.getCode().name();
            PredictionSelectionResponse c = cachedByCode.get(code);
            PredictionSelection dbSel = bestDbByMarket.get(code);

            boolean isRecommended = code.equals(recommendedMarketCode) || (c != null && selectionId != null && selectionId.equals(c.selectionId()));

            if (c != null) {
                markets.add(new FixturePredictionDetailsResponse.MarketPredictionDto(
                        code,
                        market.getDisplayName(),
                        market.getMarketFamily().name(),
                        c.probability(),
                        c.confidenceBand() != null ? c.confidenceBand() : "UNRATED",
                        isRecommended,
                        true,
                        c.reason() != null ? c.reason() : "Generated prediction",
                        c.rawModelProbability(),
                        c.decimalOdds(),
                        c.bookmakerImpliedProbability(),
                        c.probabilityEdge(),
                        null
                ));
            } else if (dbSel != null) {
                markets.add(new FixturePredictionDetailsResponse.MarketPredictionDto(
                        code,
                        market.getDisplayName(),
                        market.getMarketFamily().name(),
                        dbSel.getProbability(),
                        dbSel.getConfidenceBand() != null ? dbSel.getConfidenceBand().name() : "UNRATED",
                        isRecommended,
                        true,
                        dbSel.getCalibrationNote() != null ? dbSel.getCalibrationNote() : "Calculated from historical data and team strength.",
                        dbSel.getRawProbability(),
                        dbSel.getBestDecimalOdds(),
                        dbSel.getBestImpliedProbability(),
                        dbSel.getValueEdge(),
                        null
                ));
            } else if (isRecommended) {
                markets.add(new FixturePredictionDetailsResponse.MarketPredictionDto(
                        code,
                        market.getDisplayName(),
                        market.getMarketFamily().name(),
                        java.math.BigDecimal.valueOf(0.65),
                        summary.confidenceLevel() != null ? summary.confidenceLevel() : "MEDIUM",
                        true,
                        true,
                        summary.reasonQualified() != null ? summary.reasonQualified() : "Recommended model pick",
                        null,
                        null,
                        null,
                        null,
                        null
                ));
            } else {
                unavailableMarkets.add(new FixturePredictionDetailsResponse.UnavailableMarketDto(
                        code,
                        market.getDisplayName(),
                        market.getMarketFamily().name(),
                        false,
                        "Market is not supported by the current prediction engine or insufficient data."
                ));
            }
        }

        FixturePredictionDetailsResponse.FixtureDto fixtureDto = new FixturePredictionDetailsResponse.FixtureDto(
                match.getId(),
                match.getHomeTeam().getCanonicalName(),
                match.getHomeTeam().getBadgeUrl(),
                match.getHomeTeam().getLogoUrl(),
                match.getAwayTeam().getCanonicalName(),
                match.getAwayTeam().getBadgeUrl(),
                match.getAwayTeam().getLogoUrl(),
                match.getLeague().getName(),
                match.getLeague().getBadgeUrl(),
                match.getLeague().getLogoUrl(),
                match.getKickoffAt(),
                match.getStatus().name(),
                match.getVenue(),
                match.getHomeScore(),
                match.getAwayScore(),
                match.getLiveMinute()
        );

        List<Match> homeMatchesRaw = matchRepository.findRecentFinishedMatchesByTeamId(match.getHomeTeam().getId(), match.getMatchDate());
        List<Match> awayMatchesRaw = matchRepository.findRecentFinishedMatchesByTeamId(match.getAwayTeam().getId(), match.getMatchDate());

        List<Match> homeLast5Matches = safeList(homeMatchesRaw).stream().limit(5).toList();
        List<Match> awayLast5Matches = safeList(awayMatchesRaw).stream().limit(5).toList();
        List<Match> homeLast10Matches = safeList(homeMatchesRaw).stream().limit(10).toList();
        List<Match> awayLast10Matches = safeList(awayMatchesRaw).stream().limit(10).toList();

        List<FixturePredictionDetailsResponse.TeamRecentMatchDto> homeLast5 = buildTeamRecentMatches(homeLast5Matches, match.getHomeTeam().getId());
        List<FixturePredictionDetailsResponse.TeamRecentMatchDto> awayLast5 = buildTeamRecentMatches(awayLast5Matches, match.getAwayTeam().getId());
        List<FixturePredictionDetailsResponse.TeamRecentMatchDto> homeLast5Home = buildTeamRecentMatches(
                homeLast10Matches.stream()
                        .filter(recentMatch -> recentMatch.getHomeTeam().getId().equals(match.getHomeTeam().getId()))
                        .limit(5)
                        .toList(),
                match.getHomeTeam().getId()
        );
        List<FixturePredictionDetailsResponse.TeamRecentMatchDto> awayLast5Away = buildTeamRecentMatches(
                awayLast10Matches.stream()
                        .filter(recentMatch -> recentMatch.getAwayTeam().getId().equals(match.getAwayTeam().getId()))
                        .limit(5)
                        .toList(),
                match.getAwayTeam().getId()
        );

        FixturePredictionDetailsResponse.TeamFormSummaryDto homeForm = buildFormSummary(homeLast5);
        FixturePredictionDetailsResponse.TeamFormSummaryDto awayForm = buildFormSummary(awayLast5);

        List<Match> h2hMatchesRaw = safeList(matchRepository.findHeadToHeadMatches(match.getHomeTeam().getId(), match.getAwayTeam().getId(), match.getMatchDate()));
        FixturePredictionDetailsResponse.HeadToHeadSummaryDto headToHead = buildH2hSummary(
                h2hMatchesRaw,
                match.getHomeTeam().getId(),
                match.getAwayTeam().getId()
        );

        FixturePredictionDetailsResponse.MarketEvidenceDto marketEvidence = buildMarketEvidence(recommendedMarketCode, homeForm, awayForm, homeLast5Matches, awayLast5Matches);
        List<Match> seasonMatches = safeList(matchRepository.findFinishedMatchesForFeatureGeneration(
                match.getLeague().getCode(),
                match.getSeasonLabel(),
                match.getMatchDate()
        ));
        FixturePredictionDetailsResponse.RankingDto ranking = buildRanking(match, seasonMatches);
        FixturePredictionDetailsResponse.PreMatchStatsDto preMatchStats = buildPreMatchStats(
                buildTeamRecentMatches(homeLast10Matches, match.getHomeTeam().getId()),
                buildTeamRecentMatches(awayLast10Matches, match.getAwayTeam().getId()),
                homeLast10Matches,
                awayLast10Matches
        );
        FixturePredictionDetailsResponse.LiveMatchStatsDto liveStats = buildLiveStats(match);
        List<FixturePredictionDetailsResponse.TrendDto> trends = buildTrends(homeForm, awayForm, headToHead, homeLast5.size(), awayLast5.size());
        FixturePredictionDetailsResponse.MatchPreviewDto matchPreview = buildMatchPreview(match, homeForm, awayForm, headToHead, homeLast5.size(), awayLast5.size());

        String note = (runId != null)
                ? "Supporting stats are reconstructed from the current local database and may differ slightly from the original generation snapshot."
                : "Supporting statistics generated from local database records.";

        return new FixturePredictionDetailsResponse(
                fixtureDto, summary, markets, unavailableMarkets,
                homeLast5, awayLast5, homeLast5Home, awayLast5Away, homeForm, awayForm, headToHead, marketEvidence,
                ranking, preMatchStats, liveStats, trends, matchPreview, note
        );
    }

    private List<Match> safeList(List<Match> matches) {
        return matches == null ? List.of() : matches;
    }

    private List<FixturePredictionDetailsResponse.TeamRecentMatchDto> buildTeamRecentMatches(List<Match> matches, UUID teamId) {
        List<FixturePredictionDetailsResponse.TeamRecentMatchDto> list = new ArrayList<>();
        for (Match m : matches) {
            boolean isHome = m.getHomeTeam().getId().equals(teamId);
            String opponent = isHome ? m.getAwayTeam().getCanonicalName() : m.getHomeTeam().getCanonicalName();
            String homeOrAway = isHome ? "HOME" : "AWAY";
            int goalsFor = isHome ? (m.getHomeScore() != null ? m.getHomeScore() : 0) : (m.getAwayScore() != null ? m.getAwayScore() : 0);
            int goalsAgainst = isHome ? (m.getAwayScore() != null ? m.getAwayScore() : 0) : (m.getHomeScore() != null ? m.getHomeScore() : 0);
            String score = goalsFor + " - " + goalsAgainst;
            String result = goalsFor > goalsAgainst ? "W" : (goalsFor == goalsAgainst ? "D" : "L");
            boolean cleanSheet = (goalsAgainst == 0);
            boolean btts = (goalsFor > 0 && goalsAgainst > 0);
            list.add(new FixturePredictionDetailsResponse.TeamRecentMatchDto(
                    m.getMatchDate().toString(), opponent, homeOrAway, score, result,
                    goalsFor, goalsAgainst, m.getLeague().getName(), cleanSheet, btts
            ));
        }
        return list;
    }

    private FixturePredictionDetailsResponse.TeamFormSummaryDto buildFormSummary(List<FixturePredictionDetailsResponse.TeamRecentMatchDto> matches) {
        if (matches.isEmpty()) {
            return new FixturePredictionDetailsResponse.TeamFormSummaryDto("N/A", 0, 0, 0.0, 0.0, 0, 0, 0, 0, 0);
        }
        String formString = matches.stream().map(FixturePredictionDetailsResponse.TeamRecentMatchDto::result).collect(Collectors.joining("-"));
        int goalsScored = matches.stream().mapToInt(FixturePredictionDetailsResponse.TeamRecentMatchDto::goalsFor).sum();
        int goalsConceded = matches.stream().mapToInt(FixturePredictionDetailsResponse.TeamRecentMatchDto::goalsAgainst).sum();
        double avgScored = Math.round((double) goalsScored / matches.size() * 100.0) / 100.0;
        double avgConceded = Math.round((double) goalsConceded / matches.size() * 100.0) / 100.0;
        int cleanSheets = (int) matches.stream().filter(m -> Boolean.TRUE.equals(m.cleanSheet())).count();
        int failedToScore = (int) matches.stream().filter(m -> m.goalsFor() == 0).count();
        int btts = (int) matches.stream().filter(m -> Boolean.TRUE.equals(m.bothTeamsScored())).count();
        int over15 = (int) matches.stream().filter(m -> (m.goalsFor() + m.goalsAgainst()) > 1).count();
        int over25 = (int) matches.stream().filter(m -> (m.goalsFor() + m.goalsAgainst()) > 2).count();

        return new FixturePredictionDetailsResponse.TeamFormSummaryDto(
                formString, goalsScored, goalsConceded, avgScored, avgConceded,
                cleanSheets, failedToScore, btts, over15, over25
        );
    }

    private FixturePredictionDetailsResponse.HeadToHeadSummaryDto buildH2hSummary(
            List<Match> matchesRaw,
            UUID homeTeamId,
            UUID awayTeamId
    ) {
        List<Match> topMatches = matchesRaw.stream()
                .filter(match -> match.getHomeScore() != null && match.getAwayScore() != null)
                .limit(10)
                .toList();
        List<FixturePredictionDetailsResponse.H2hMatchDto> dtoList = new ArrayList<>();
        int homeWins = 0, awayWins = 0, draws = 0, totalGoals = 0;
        int bttsCount = 0, over15Count = 0, over25Count = 0, over35Count = 0;
        int under25Count = 0, under35Count = 0, under45Count = 0;
        int homeScoredCount = 0, awayScoredCount = 0;

        for (Match m : topMatches) {
            int hs = m.getHomeScore();
            int as = m.getAwayScore();
            int matchGoals = hs + as;
            totalGoals += matchGoals;
            if (hs > 0 && as > 0) bttsCount++;
            if (matchGoals > 1) over15Count++;
            if (matchGoals > 2) over25Count++;
            if (matchGoals > 3) over35Count++;
            if (matchGoals < 3) under25Count++;
            if (matchGoals < 4) under35Count++;
            if (matchGoals < 5) under45Count++;

            int currentHomeTeamGoals = m.getHomeTeam().getId().equals(homeTeamId) ? hs : as;
            int currentAwayTeamGoals = m.getHomeTeam().getId().equals(awayTeamId) ? hs : as;
            if (currentHomeTeamGoals > 0) homeScoredCount++;
            if (currentAwayTeamGoals > 0) awayScoredCount++;

            String winner;
            if (hs == as) {
                winner = "DRAW";
                draws++;
            } else if (m.getHomeTeam().getId().equals(homeTeamId)) {
                if (hs > as) { winner = "HOME"; homeWins++; } else { winner = "AWAY"; awayWins++; }
            } else {
                if (as > hs) { winner = "HOME"; homeWins++; } else { winner = "AWAY"; awayWins++; }
            }

            dtoList.add(new FixturePredictionDetailsResponse.H2hMatchDto(
                    m.getMatchDate().toString(), m.getLeague().getName(),
                    m.getHomeTeam().getCanonicalName(), m.getAwayTeam().getCanonicalName(),
                    hs + " - " + as, winner
            ));
        }

        int size = topMatches.size();
        double avgGoals = size > 0 ? Math.round((double) totalGoals / size * 100.0) / 100.0 : 0.0;
        String bttsRate = size > 0 ? Math.round((double) bttsCount / size * 100) + "%" : "0%";
        String over15Rate = size > 0 ? Math.round((double) over15Count / size * 100) + "%" : "0%";
        String over25Rate = size > 0 ? Math.round((double) over25Count / size * 100) + "%" : "0%";

        return new FixturePredictionDetailsResponse.HeadToHeadSummaryDto(
                size, homeWins, awayWins, draws, avgGoals, bttsRate, over15Rate, over25Rate,
                new FixturePredictionDetailsResponse.H2hOccurrenceDto(over15Count, size),
                new FixturePredictionDetailsResponse.H2hOccurrenceDto(over35Count, size),
                new FixturePredictionDetailsResponse.H2hOccurrenceDto(under35Count, size),
                new FixturePredictionDetailsResponse.H2hOccurrenceDto(under45Count, size),
                new FixturePredictionDetailsResponse.H2hOccurrenceDto(homeScoredCount, size),
                new FixturePredictionDetailsResponse.H2hOccurrenceDto(awayScoredCount, size),
                new FixturePredictionDetailsResponse.H2hOccurrenceDto(under25Count, size),
                new FixturePredictionDetailsResponse.H2hOccurrenceDto(bttsCount, size),
                dtoList
        );
    }

    private FixturePredictionDetailsResponse.MarketEvidenceDto buildMarketEvidence(
            String marketCode,
            FixturePredictionDetailsResponse.TeamFormSummaryDto homeForm,
            FixturePredictionDetailsResponse.TeamFormSummaryDto awayForm,
            List<Match> homeMatches,
            List<Match> awayMatches
    ) {
        String hOver15 = homeForm.over15Count() + "/5 (" + Math.round(homeForm.over15Count() * 20.0) + "%)";
        String aOver15 = awayForm.over15Count() + "/5 (" + Math.round(awayForm.over15Count() * 20.0) + "%)";
        String combOver15 = (homeForm.over15Count() + awayForm.over15Count()) + "/10 (" + Math.round((homeForm.over15Count() + awayForm.over15Count()) * 10.0) + "%)";

        String hOver25 = homeForm.over25Count() + "/5 (" + Math.round(homeForm.over25Count() * 20.0) + "%)";
        String aOver25 = awayForm.over25Count() + "/5 (" + Math.round(awayForm.over25Count() * 20.0) + "%)";

        String hBtts = homeForm.bothTeamsScoredCount() + "/5 (" + Math.round(homeForm.bothTeamsScoredCount() * 20.0) + "%)";
        String aBtts = awayForm.bothTeamsScoredCount() + "/5 (" + Math.round(awayForm.bothTeamsScoredCount() * 20.0) + "%)";

        String hClean = homeForm.cleanSheets() + "/5 (" + Math.round(homeForm.cleanSheets() * 20.0) + "%)";
        String aFailed = awayForm.failedToScoreCount() + "/5 (" + Math.round(awayForm.failedToScoreCount() * 20.0) + "%)";

        List<Match> allRecent = new ArrayList<>(homeMatches);
        allRecent.addAll(awayMatches);

        int cornerMatches = 0;
        int totalCorners = 0;
        for (Match m : allRecent) {
            if (m.getStatistics() != null && m.getStatistics().getHomeCorners() != null && m.getStatistics().getAwayCorners() != null) {
                cornerMatches++;
                totalCorners += (m.getStatistics().getHomeCorners() + m.getStatistics().getAwayCorners());
            }
        }
        String cornersInfo = cornerMatches > 0
                ? "Avg " + Math.round((double) totalCorners / cornerMatches * 10.0) / 10.0 + " total corners per match"
                : "Corner data unavailable";

        int cardMatches = 0;
        int totalCards = 0;
        for (Match m : allRecent) {
            if (m.getStatistics() != null && m.getStatistics().getHomeYellowCards() != null && m.getStatistics().getAwayYellowCards() != null) {
                cardMatches++;
                totalCards += (m.getStatistics().getHomeYellowCards() + m.getStatistics().getAwayYellowCards());
            }
        }
        String cardsInfo = cardMatches > 0
                ? "Avg " + Math.round((double) totalCards / cardMatches * 10.0) / 10.0 + " cards per match"
                : "Card data unavailable";

        return new FixturePredictionDetailsResponse.MarketEvidenceDto(
                marketCode, hOver15, aOver15, combOver15, hOver25, aOver25,
                hBtts, aBtts, hClean, aFailed, cornersInfo, cardsInfo
        );
    }

    private FixturePredictionDetailsResponse.RankingDto buildRanking(Match fixture, List<Match> finishedSeasonMatches) {
        if (finishedSeasonMatches == null || finishedSeasonMatches.isEmpty()) {
            return new FixturePredictionDetailsResponse.RankingDto(
                    false,
                    "Calculated from local finished matches",
                    fixture.getSeasonLabel(),
                    "Ranking data is not available for this competition yet.",
                    List.of()
            );
        }

        Map<UUID, StandingAccumulator> table = new LinkedHashMap<>();
        for (Match match : finishedSeasonMatches) {
            StandingAccumulator home = table.computeIfAbsent(match.getHomeTeam().getId(), id -> new StandingAccumulator(match.getHomeTeam()));
            StandingAccumulator away = table.computeIfAbsent(match.getAwayTeam().getId(), id -> new StandingAccumulator(match.getAwayTeam()));
            int homeScore = match.getHomeScore() != null ? match.getHomeScore() : 0;
            int awayScore = match.getAwayScore() != null ? match.getAwayScore() : 0;
            home.apply(homeScore, awayScore);
            away.apply(awayScore, homeScore);
        }

        List<StandingAccumulator> sorted = new ArrayList<>(table.values());
        sorted.sort(Comparator
                .comparingInt(StandingAccumulator::points).reversed()
                .thenComparing(Comparator.comparingInt(StandingAccumulator::goalDifference).reversed())
                .thenComparing(Comparator.comparingInt(StandingAccumulator::goalsFor).reversed())
                .thenComparing(a -> a.team.getCanonicalName()));

        List<FixturePredictionDetailsResponse.TeamStandingDto> rows = new ArrayList<>();
        for (int index = 0; index < sorted.size(); index++) {
            StandingAccumulator row = sorted.get(index);
            rows.add(new FixturePredictionDetailsResponse.TeamStandingDto(
                    index + 1,
                    row.team.getId(),
                    row.team.getCanonicalName(),
                    row.team.getBadgeUrl(),
                    row.team.getLogoUrl(),
                    row.played,
                    row.wins,
                    row.draws,
                    row.losses,
                    row.goalsFor,
                    row.goalsAgainst,
                    row.goalDifference(),
                    row.points(),
                    row.played > 0 ? Math.round((double) row.points() / row.played * 100.0) / 100.0 : 0.0,
                    row.lastFive(),
                    row.team.getId().equals(fixture.getHomeTeam().getId()) || row.team.getId().equals(fixture.getAwayTeam().getId())
            ));
        }

        return new FixturePredictionDetailsResponse.RankingDto(
                true,
                "Calculated from local finished matches",
                fixture.getSeasonLabel(),
                null,
                rows
        );
    }

    private FixturePredictionDetailsResponse.PreMatchStatsDto buildPreMatchStats(
            List<FixturePredictionDetailsResponse.TeamRecentMatchDto> home,
            List<FixturePredictionDetailsResponse.TeamRecentMatchDto> away,
            List<Match> homeRaw,
            List<Match> awayRaw
    ) {
        List<String> lines = List.of("0.5", "1.5", "2.5", "3.5", "4.5", "5.5", "6.5", "7.5");
        List<FixturePredictionDetailsResponse.OverUnderComparisonDto> overUnder = lines.stream()
                .map(line -> {
                    double threshold = Double.parseDouble(line);
                    return new FixturePredictionDetailsResponse.OverUnderComparisonDto(
                            line,
                            buildOverUnder(home, threshold),
                            buildOverUnder(away, threshold)
                    );
                })
                .toList();
        int sample = Math.max(home.size(), away.size());
        String sampleLabel = sample > 0 ? "Based on last " + sample + " available matches" : "Not enough match history to calculate pre-match stats.";
        return new FixturePredictionDetailsResponse.PreMatchStatsDto(
                sampleLabel,
                overUnder,
                buildRate(home, m -> Boolean.TRUE.equals(m.bothTeamsScored())),
                buildRate(away, m -> Boolean.TRUE.equals(m.bothTeamsScored())),
                buildRate(home, m -> Boolean.TRUE.equals(m.cleanSheet())),
                buildRate(away, m -> Boolean.TRUE.equals(m.cleanSheet())),
                buildRate(home, m -> m.goalsFor() == 0),
                buildRate(away, m -> m.goalsFor() == 0),
                hasCornerData(homeRaw, awayRaw) ? "Corner data available in recent local matches." : "Corners data is unavailable for this fixture set.",
                hasCardData(homeRaw, awayRaw) ? "Card data available in recent local matches." : "Cards data is unavailable for this fixture set."
        );
    }

    private FixturePredictionDetailsResponse.LiveMatchStatsDto buildLiveStats(Match match) {
        List<FixturePredictionDetailsResponse.LiveStatRowDto> rows = new ArrayList<>();
        MatchStatistics stats = match.getStatistics();
        if (stats != null) {
            addLiveStat(rows, "POSSESSION", "Possession", percentValue(stats.getHomePossession()), percentValue(stats.getAwayPossession()), "PERCENT");
            addLiveStat(rows, "SHOTS", "Shots", stats.getHomeShots(), stats.getAwayShots(), "NUMBER");
            addLiveStat(rows, "SHOTS_ON_TARGET", "Shots on target", stats.getHomeShotsOnTarget(), stats.getAwayShotsOnTarget(), "NUMBER");
            addLiveStat(rows, "CORNERS", "Corners", stats.getHomeCorners(), stats.getAwayCorners(), "NUMBER");
            addLiveStat(rows, "FOULS", "Fouls", stats.getHomeFouls(), stats.getAwayFouls(), "NUMBER");
            addLiveStat(rows, "YELLOW_CARDS", "Yellow cards", stats.getHomeYellowCards(), stats.getAwayYellowCards(), "NUMBER");
            addLiveStat(rows, "RED_CARDS", "Red cards", stats.getHomeRedCards(), stats.getAwayRedCards(), "NUMBER");
            addLiveStat(rows, "EXPECTED_GOALS", "Expected goals", decimalValue(stats.getHomeExpectedGoals()), decimalValue(stats.getAwayExpectedGoals()), "DECIMAL");
        }

        boolean live = match.getStatus() == MatchStatus.LIVE;
        if (rows.isEmpty()) {
            String reason = live
                    ? "Live match stats are not available yet. They will appear after the next live score/stat refresh when live statistics are returned."
                    : "Live match stats appear here when this fixture is in play or when local event statistics exist.";
            return new FixturePredictionDetailsResponse.LiveMatchStatsDto(
                    false,
                    liveStatusLabel(match),
                    match.getScoreRefreshedAt(),
                    reason,
                    List.of()
            );
        }

        return new FixturePredictionDetailsResponse.LiveMatchStatsDto(
                true,
                liveStatusLabel(match),
                match.getScoreRefreshedAt(),
                null,
                rows
        );
    }

    private void addLiveStat(
            List<FixturePredictionDetailsResponse.LiveStatRowDto> rows,
            String code,
            String label,
            Object home,
            Object away,
            String displayType
    ) {
        String homeValue = home == null ? null : home.toString();
        String awayValue = away == null ? null : away.toString();
        if (homeValue == null && awayValue == null) {
            return;
        }
        rows.add(new FixturePredictionDetailsResponse.LiveStatRowDto(
                code,
                label,
                homeValue == null ? "--" : homeValue,
                awayValue == null ? "--" : awayValue,
                displayType
        ));
    }

    private String percentValue(Integer value) {
        return value == null ? null : value + "%";
    }

    private String decimalValue(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private String liveStatusLabel(Match match) {
        if (match.getStatus() == MatchStatus.LIVE) {
            return match.getLiveMinute() == null || match.getLiveMinute().isBlank()
                    ? "Live"
                    : "Live " + match.getLiveMinute();
        }
        return match.getStatus() == null ? "Status unavailable" : match.getStatus().name();
    }

    private FixturePredictionDetailsResponse.TeamOverUnderDto buildOverUnder(List<FixturePredictionDetailsResponse.TeamRecentMatchDto> matches, double threshold) {
        int sample = matches.size();
        int over = (int) matches.stream().filter(match -> match.goalsFor() + match.goalsAgainst() > threshold).count();
        int under = sample - over;
        return new FixturePredictionDetailsResponse.TeamOverUnderDto(sample, under, over, percent(under, sample), percent(over, sample));
    }

    private FixturePredictionDetailsResponse.TeamRateDto buildRate(
            List<FixturePredictionDetailsResponse.TeamRecentMatchDto> matches,
            java.util.function.Predicate<FixturePredictionDetailsResponse.TeamRecentMatchDto> predicate
    ) {
        int sample = matches.size();
        int count = (int) matches.stream().filter(predicate).count();
        return new FixturePredictionDetailsResponse.TeamRateDto(sample, count, percent(count, sample));
    }

    private String percent(int count, int sample) {
        if (sample <= 0) return "--";
        return Math.round((double) count / sample * 100) + "%";
    }

    private boolean hasCornerData(List<Match> homeRaw, List<Match> awayRaw) {
        return concat(homeRaw, awayRaw).stream().anyMatch(match -> match.getStatistics() != null
                && match.getStatistics().getHomeCorners() != null
                && match.getStatistics().getAwayCorners() != null);
    }

    private boolean hasCardData(List<Match> homeRaw, List<Match> awayRaw) {
        return concat(homeRaw, awayRaw).stream().anyMatch(match -> match.getStatistics() != null
                && match.getStatistics().getHomeYellowCards() != null
                && match.getStatistics().getAwayYellowCards() != null);
    }

    private List<Match> concat(List<Match> first, List<Match> second) {
        List<Match> combined = new ArrayList<>();
        combined.addAll(first == null ? Collections.emptyList() : first);
        combined.addAll(second == null ? Collections.emptyList() : second);
        return combined;
    }

    private List<FixturePredictionDetailsResponse.TrendDto> buildTrends(
            FixturePredictionDetailsResponse.TeamFormSummaryDto home,
            FixturePredictionDetailsResponse.TeamFormSummaryDto away,
            FixturePredictionDetailsResponse.HeadToHeadSummaryDto h2h,
            int homeSample,
            int awaySample
    ) {
        List<FixturePredictionDetailsResponse.TrendDto> trends = new ArrayList<>();
        addTrend(trends, "GOALS", "Home scored", homeSample - home.failedToScoreCount(), homeSample, "Home team scored in recent local matches.");
        addTrend(trends, "GOALS", "Away scored", awaySample - away.failedToScoreCount(), awaySample, "Away team scored in recent local matches.");
        addTrend(trends, "GOALS", "Home conceded", homeSample - home.cleanSheets(), homeSample, "Home team conceded in recent local matches.");
        addTrend(trends, "GOALS", "Away conceded", awaySample - away.cleanSheets(), awaySample, "Away team conceded in recent local matches.");
        addTrend(trends, "GOALS", "Home over 1.5", home.over15Count(), homeSample, "Total match goals over 1.5 in home team's recent matches.");
        addTrend(trends, "GOALS", "Away over 1.5", away.over15Count(), awaySample, "Total match goals over 1.5 in away team's recent matches.");
        addTrend(trends, "GOALS", "Home over 2.5", home.over25Count(), homeSample, "Total match goals over 2.5 in home team's recent matches.");
        addTrend(trends, "GOALS", "Away over 2.5", away.over25Count(), awaySample, "Total match goals over 2.5 in away team's recent matches.");
        addTrend(trends, "BTTS", "Home BTTS Yes", home.bothTeamsScoredCount(), homeSample, "Both teams scored in home team's recent matches.");
        addTrend(trends, "BTTS", "Away BTTS Yes", away.bothTeamsScoredCount(), awaySample, "Both teams scored in away team's recent matches.");
        addTrend(trends, "DEFENCE", "Home clean sheets", home.cleanSheets(), homeSample, "Home clean sheets in recent matches.");
        addTrend(trends, "DEFENCE", "Away clean sheets", away.cleanSheets(), awaySample, "Away clean sheets in recent matches.");
        if (h2h.totalMatches() > 0) {
            trends.add(new FixturePredictionDetailsResponse.TrendDto("H2H", "H2H BTTS", h2h.bttsRate(), "Both teams scored rate in local head-to-head matches.", h2h.totalMatches()));
            trends.add(new FixturePredictionDetailsResponse.TrendDto("H2H", "H2H over 2.5", h2h.over25Rate(), "Over 2.5 rate in local head-to-head matches.", h2h.totalMatches()));
        }
        return trends;
    }

    private void addTrend(List<FixturePredictionDetailsResponse.TrendDto> trends, String category, String label, int count, int sample, String detail) {
        if (sample <= 0) return;
        trends.add(new FixturePredictionDetailsResponse.TrendDto(category, label, count + "/" + sample + " (" + percent(count, sample) + ")", detail, sample));
    }

    private FixturePredictionDetailsResponse.MatchPreviewDto buildMatchPreview(
            Match fixture,
            FixturePredictionDetailsResponse.TeamFormSummaryDto home,
            FixturePredictionDetailsResponse.TeamFormSummaryDto away,
            FixturePredictionDetailsResponse.HeadToHeadSummaryDto h2h,
            int homeSample,
            int awaySample
    ) {
        List<String> limitations = new ArrayList<>();
        if (homeSample < 5) limitations.add("Limited recent match history for " + fixture.getHomeTeam().getCanonicalName() + ".");
        if (awaySample < 5) limitations.add("Limited recent match history for " + fixture.getAwayTeam().getCanonicalName() + ".");
        if (h2h.totalMatches() == 0) limitations.add("No head-to-head matches found in the local database.");
        String text = "Based on local match history, "
                + fixture.getHomeTeam().getCanonicalName() + " have " + home.formString() + " form across their available recent matches and average "
                + home.avgGoalsScored() + " goals scored. "
                + fixture.getAwayTeam().getCanonicalName() + " have " + away.formString() + " form and average "
                + away.avgGoalsScored() + " goals scored. "
                + "The strongest recent goal trend is "
                + strongerTrendLabel(home, away)
                + ". Treat this as supporting evidence, not certainty.";
        return new FixturePredictionDetailsResponse.MatchPreviewDto(text, limitations);
    }

    private String strongerTrendLabel(FixturePredictionDetailsResponse.TeamFormSummaryDto home, FixturePredictionDetailsResponse.TeamFormSummaryDto away) {
        int under35 = (5 - home.over25Count()) + (5 - away.over25Count());
        int btts = home.bothTeamsScoredCount() + away.bothTeamsScoredCount();
        return under35 >= btts ? "controlled scoring, with several recent matches staying below higher goal lines" : "both teams scoring in recent matches";
    }

    private static final class StandingAccumulator {
        private final com.betai.domain.team.Team team;
        private int played;
        private int wins;
        private int draws;
        private int losses;
        private int goalsFor;
        private int goalsAgainst;
        private final List<String> form = new ArrayList<>();

        private StandingAccumulator(com.betai.domain.team.Team team) {
            this.team = team;
        }

        private void apply(int scored, int conceded) {
            played++;
            goalsFor += scored;
            goalsAgainst += conceded;
            if (scored > conceded) {
                wins++;
                form.add("W");
            } else if (scored == conceded) {
                draws++;
                form.add("D");
            } else {
                losses++;
                form.add("L");
            }
        }

        private int points() {
            return wins * 3 + draws;
        }

        private int goalsFor() {
            return goalsFor;
        }

        private int goalDifference() {
            return goalsFor - goalsAgainst;
        }

        private List<String> lastFive() {
            int from = Math.max(0, form.size() - 5);
            return form.subList(from, form.size());
        }
    }
}
