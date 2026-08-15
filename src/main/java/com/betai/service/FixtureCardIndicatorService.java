package com.betai.service;

import com.betai.api.dto.PredictionBatchResponse;
import com.betai.api.dto.PredictionFixtureIndicatorsResponse;
import com.betai.api.dto.PredictionSelectionResponse;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.match.Match;
import com.betai.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FixtureCardIndicatorService {

    private static final int RECENT_FORM_MATCH_LIMIT = 5;

    private final MatchRepository matchRepository;

    @Transactional(readOnly = true)
    public Map<UUID, PredictionFixtureIndicatorsResponse> build(List<PredictionBatchResponse> batches) {
        List<PredictionSelectionResponse> selections = batches == null
                ? List.of()
                : batches.stream()
                .flatMap(batch -> batch.selections().stream())
                .filter(selection -> selection.selectionId() != null && selection.matchId() != null)
                .toList();
        if (selections.isEmpty()) {
            return Map.of();
        }

        Set<UUID> matchIds = selections.stream()
                .map(PredictionSelectionResponse::matchId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, Match> fixtures = matchRepository.findAllForFixtureIndicators(matchIds).stream()
                .collect(Collectors.toMap(Match::getId, match -> match));
        if (fixtures.isEmpty()) {
            return Map.of();
        }

        Set<UUID> teamIds = fixtures.values().stream()
                .flatMap(match -> java.util.stream.Stream.of(match.getHomeTeam().getId(), match.getAwayTeam().getId()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<LeagueCode> leagueCodes = fixtures.values().stream()
                .map(match -> match.getLeague().getCode())
                .filter(LeagueCode::isLeagueCompetition)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> seasonLabels = fixtures.values().stream()
                .filter(match -> match.getLeague().getCode().isLeagueCompetition())
                .map(Match::getSeasonLabel)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        OffsetDateTime latestKickoff = fixtures.values().stream()
                .map(Match::getKickoffAt)
                .max(OffsetDateTime::compareTo)
                .orElseThrow();

        List<Match> teamHistory = safe(matchRepository.findFinishedMatchesForFixtureIndicators(teamIds, latestKickoff));
        List<Match> leagueHistory = leagueCodes.isEmpty() || seasonLabels.isEmpty()
                ? List.of()
                : safe(matchRepository.findFinishedLeagueMatchesForFixtureIndicators(leagueCodes, seasonLabels, latestKickoff));

        Map<UUID, BaseIndicators> baseByMatch = new HashMap<>();
        for (Match fixture : fixtures.values()) {
            baseByMatch.put(fixture.getId(), indicatorsFor(fixture, teamHistory, leagueHistory));
        }

        Map<UUID, PredictionFixtureIndicatorsResponse> bySelection = new LinkedHashMap<>();
        for (PredictionSelectionResponse selection : selections) {
            BaseIndicators base = baseByMatch.get(selection.matchId());
            if (base == null) {
                continue;
            }
            boolean partialSeason = isPartialCoverage(selection.marketSpecificDataCoverage());
            bySelection.put(selection.selectionId(), new PredictionFixtureIndicatorsResponse(
                    base.h2hMatchCount() > 0,
                    base.h2hMatchCount() > 0 ? base.h2hMatchCount() : null,
                    base.homeLeaguePosition(),
                    base.awayLeaguePosition(),
                    base.leagueTableTeamCount(),
                    partialSeason,
                    partialSeason ? selection.marketSpecificDataCoverage() : null,
                    base.homeForm().percentage(),
                    base.awayForm().percentage(),
                    base.homeForm().sampleSize(),
                    base.awayForm().sampleSize()
            ));
        }
        return Map.copyOf(bySelection);
    }

    private BaseIndicators indicatorsFor(Match fixture, List<Match> teamHistory, List<Match> leagueHistory) {
        List<Match> priorTeamMatches = teamHistory.stream()
                .filter(match -> match.getKickoffAt().isBefore(fixture.getKickoffAt()))
                .toList();
        UUID homeTeamId = fixture.getHomeTeam().getId();
        UUID awayTeamId = fixture.getAwayTeam().getId();

        int h2hCount = (int) priorTeamMatches.stream()
                .filter(match -> involves(match, homeTeamId) && involves(match, awayTeamId))
                .count();
        FormPercentage homeForm = recentForm(priorTeamMatches, homeTeamId);
        FormPercentage awayForm = recentForm(priorTeamMatches, awayTeamId);

        LeaguePositions positions = fixture.getLeague().getCode().isLeagueCompetition()
                ? leaguePositions(fixture, leagueHistory)
                : LeaguePositions.unavailable();
        return new BaseIndicators(
                h2hCount,
                positions.homePosition(),
                positions.awayPosition(),
                positions.teamCount(),
                homeForm,
                awayForm
        );
    }

    private FormPercentage recentForm(List<Match> history, UUID teamId) {
        List<Match> recent = history.stream()
                .filter(match -> involves(match, teamId))
                .sorted(Comparator.comparing(Match::getKickoffAt).reversed())
                .limit(RECENT_FORM_MATCH_LIMIT)
                .toList();
        if (recent.isEmpty()) {
            return FormPercentage.unavailable();
        }
        int points = recent.stream().mapToInt(match -> pointsFor(match, teamId)).sum();
        int percentage = (int) Math.round(points * 100.0 / (recent.size() * 3.0));
        return new FormPercentage(percentage, recent.size());
    }

    private LeaguePositions leaguePositions(Match fixture, List<Match> leagueHistory) {
        List<Match> relevant = leagueHistory.stream()
                .filter(match -> match.getLeague().getCode() == fixture.getLeague().getCode())
                .filter(match -> fixture.getSeasonLabel().equals(match.getSeasonLabel()))
                .filter(match -> match.getKickoffAt().isBefore(fixture.getKickoffAt()))
                .toList();
        if (relevant.isEmpty()) {
            return LeaguePositions.unavailable();
        }

        Map<UUID, Standing> table = new HashMap<>();
        for (Match match : relevant) {
            table.computeIfAbsent(match.getHomeTeam().getId(), id -> new Standing(match.getHomeTeam().getCanonicalName()))
                    .apply(match.getHomeScore(), match.getAwayScore());
            table.computeIfAbsent(match.getAwayTeam().getId(), id -> new Standing(match.getAwayTeam().getCanonicalName()))
                    .apply(match.getAwayScore(), match.getHomeScore());
        }

        List<Map.Entry<UUID, Standing>> sorted = new ArrayList<>(table.entrySet());
        sorted.sort(Map.Entry.<UUID, Standing>comparingByValue(Comparator
                .comparingInt(Standing::points).reversed()
                .thenComparing(Comparator.comparingInt(Standing::goalDifference).reversed())
                .thenComparing(Comparator.comparingInt(Standing::goalsFor).reversed())
                .thenComparing(Standing::teamName)));

        Integer homePosition = null;
        Integer awayPosition = null;
        for (int index = 0; index < sorted.size(); index++) {
            UUID teamId = sorted.get(index).getKey();
            if (teamId.equals(fixture.getHomeTeam().getId())) {
                homePosition = index + 1;
            }
            if (teamId.equals(fixture.getAwayTeam().getId())) {
                awayPosition = index + 1;
            }
        }
        if (homePosition == null || awayPosition == null) {
            return LeaguePositions.unavailable();
        }
        return new LeaguePositions(homePosition, awayPosition, sorted.size());
    }

    private int pointsFor(Match match, UUID teamId) {
        boolean home = match.getHomeTeam().getId().equals(teamId);
        int goalsFor = home ? match.getHomeScore() : match.getAwayScore();
        int goalsAgainst = home ? match.getAwayScore() : match.getHomeScore();
        return goalsFor > goalsAgainst ? 3 : goalsFor == goalsAgainst ? 1 : 0;
    }

    private boolean involves(Match match, UUID teamId) {
        return match.getHomeTeam().getId().equals(teamId) || match.getAwayTeam().getId().equals(teamId);
    }

    private boolean isPartialCoverage(String coverage) {
        return StringUtils.hasText(coverage)
                && coverage.trim().toUpperCase(java.util.Locale.ROOT).endsWith(":PARTIAL");
    }

    private List<Match> safe(List<Match> matches) {
        return matches == null ? List.of() : matches;
    }

    private record BaseIndicators(
            int h2hMatchCount,
            Integer homeLeaguePosition,
            Integer awayLeaguePosition,
            Integer leagueTableTeamCount,
            FormPercentage homeForm,
            FormPercentage awayForm
    ) {
    }

    private record FormPercentage(Integer percentage, Integer sampleSize) {
        private static FormPercentage unavailable() {
            return new FormPercentage(null, null);
        }
    }

    private record LeaguePositions(Integer homePosition, Integer awayPosition, Integer teamCount) {
        private static LeaguePositions unavailable() {
            return new LeaguePositions(null, null, null);
        }
    }

    private static final class Standing {
        private final String teamName;
        private int points;
        private int goalsFor;
        private int goalsAgainst;

        private Standing(String teamName) {
            this.teamName = teamName;
        }

        private void apply(int scored, int conceded) {
            goalsFor += scored;
            goalsAgainst += conceded;
            points += scored > conceded ? 3 : scored == conceded ? 1 : 0;
        }

        private String teamName() {
            return teamName;
        }

        private int points() {
            return points;
        }

        private int goalsFor() {
            return goalsFor;
        }

        private int goalDifference() {
            return goalsFor - goalsAgainst;
        }
    }
}
