package com.betai.service;

import com.betai.config.PredictionProperties;
import com.betai.domain.feature.FeatureGroup;
import com.betai.domain.feature.HistoricalDepthStatus;
import com.betai.domain.feature.SeasonSelectionMode;
import com.betai.domain.feature.SeasonUsabilityStatus;
import com.betai.domain.league.CompetitionHistoryPolicy;
import com.betai.domain.league.League;
import com.betai.domain.match.MatchStatus;
import com.betai.domain.source.CoverageLevel;
import com.betai.domain.source.ExternalEntityType;
import com.betai.domain.source.ExternalSourceMapping;
import com.betai.domain.source.ExternalSourceType;
import com.betai.domain.source.LeagueSeasonCoverage;
import com.betai.exception.InvalidRequestException;
import com.betai.repository.ExternalSourceMappingRepository;
import com.betai.repository.LeagueSeasonCoverageRepository;
import com.betai.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HistoricalSeasonWindowServiceImpl implements HistoricalSeasonWindowService {

    private static final Pattern YEAR_PATTERN = Pattern.compile("\\d{4}");
    private static final String ROLLING_YEARS_WINDOW_PREFIX = "ROLLING_YEARS";

    private final PredictionProperties predictionProperties;
    private final MatchRepository matchRepository;
    private final LeagueSeasonCoverageRepository coverageRepository;
    private final ExternalSourceMappingRepository externalSourceMappingRepository;
    private final SeasonRecencyWeightingPolicy recencyWeightingPolicy;
    private final CompetitionHistoryPolicyService competitionHistoryPolicyService;

    @Override
    public HistoricalSeasonWindow resolveWindow(
            League league,
            LocalDate cutoffDate,
            Integer requestedSeasonCount,
            SeasonSelectionMode seasonSelectionMode,
            Set<String> customSeasonIds,
            FeatureGroup featureGroup
    ) {
        int resolvedRequested = resolveRequestedSeasonCount(requestedSeasonCount);
        boolean defaultApplied = requestedSeasonCount == null;
        SeasonSelectionMode resolvedMode = seasonSelectionMode == null
                ? SeasonSelectionMode.CURRENT_AND_RECENT_COMPLETE
                : seasonSelectionMode;
        FeatureGroup resolvedGroup = featureGroup == null ? FeatureGroup.RESULTS : featureGroup;

        if (competitionHistoryPolicyService.policyFor(league) == CompetitionHistoryPolicy.INTERNATIONAL_FOUR_YEAR_WINDOW
                && resolvedMode != SeasonSelectionMode.CUSTOM) {
            return resolveWorldCupRollingWindow(
                    league,
                    cutoffDate,
                    CompetitionHistoryPolicyService.INTERNATIONAL_HISTORY_WINDOW_YEARS,
                    defaultApplied,
                    resolvedMode,
                    resolvedGroup
            );
        }

        Set<String> discovered = discoveredSeasons(league);
        Set<String> imported = new LinkedHashSet<>(matchRepository.findDistinctSeasonLabelsByLeagueCode(league.getCode()));
        Set<String> candidates = candidates(league, resolvedMode, customSeasonIds, discovered, imported);
        List<SeasonCandidate> usable = candidates.stream()
                .map(season -> seasonCandidate(league, season, cutoffDate, resolvedGroup))
                .filter(candidate -> candidate.usabilityStatus() == SeasonUsabilityStatus.FULL
                        || candidate.usabilityStatus() == SeasonUsabilityStatus.PARTIAL)
                .sorted(Comparator.comparingInt((SeasonCandidate candidate) -> seasonSortKey(candidate.season())).reversed())
                .limit(resolvedRequested)
                .toList();

        List<String> selected = usable.stream().map(SeasonCandidate::season).toList();
        int completedMatches = usable.stream().mapToInt(SeasonCandidate::completedMatches).sum();
        int actual = selected.size();
        boolean currentIncluded = selected.contains(league.getCurrentSeason());
        boolean fallbackApplied = actual < resolvedRequested;
        HistoricalDepthStatus depthStatus = depthStatus(resolvedRequested, actual, currentIncluded);
        List<BigDecimal> weights = recencyWeightingPolicy.weights(actual);

        return new HistoricalSeasonWindow(
                resolvedRequested,
                defaultApplied,
                discovered.size(),
                imported.size(),
                (int) candidates.stream()
                        .map(season -> seasonCandidate(league, season, cutoffDate, resolvedGroup))
                        .filter(candidate -> candidate.usabilityStatus() == SeasonUsabilityStatus.FULL
                                || candidate.usabilityStatus() == SeasonUsabilityStatus.PARTIAL)
                        .count(),
                actual,
                resolvedMode,
                selected,
                selected,
                currentIncluded,
                fallbackApplied,
                selected.isEmpty() ? null : oldestDate(league, selected, cutoffDate),
                selected.isEmpty() ? null : cutoffDate,
                completedMatches,
                resolvedGroup,
                actual,
                coverageLabel(resolvedGroup, usable),
                depthStatus,
                recencyWeightingPolicy.version(),
                weights,
                seasonWindowKey(resolvedMode, resolvedRequested, resolvedGroup, selected)
        );
    }

    private HistoricalSeasonWindow resolveWorldCupRollingWindow(
            League league,
            LocalDate cutoffDate,
            int requestedYears,
            boolean defaultApplied,
            SeasonSelectionMode mode,
            FeatureGroup featureGroup
    ) {
        LocalDate fromDate = cutoffDate.minusYears(requestedYears);
        int completedMatches = safeInt(matchRepository.countFinishedMatchesByLeagueCodeAndMatchDateBetween(
                league.getCode(),
                fromDate,
                cutoffDate
        ));
        boolean usable = completedMatches >= Math.max(1, predictionProperties.minimumCompletedMatchesPerUsableSeason());
        int actualYearsUsed = usable ? requestedYears : 0;
        List<String> selected = usable
                ? List.of(rollingWindowId(requestedYears, fromDate, cutoffDate))
                : List.of();
        String coverageLabel = rollingWindowCoverageLabel(featureGroup, completedMatches, requestedYears);

        return new HistoricalSeasonWindow(
                requestedYears,
                defaultApplied,
                selected.size(),
                selected.size(),
                actualYearsUsed,
                actualYearsUsed,
                mode,
                selected,
                selected,
                usable,
                !usable,
                usable ? fromDate : null,
                usable ? cutoffDate : null,
                completedMatches,
                featureGroup,
                actualYearsUsed,
                coverageLabel,
                usable ? HistoricalDepthStatus.FULL_REQUESTED_DEPTH : HistoricalDepthStatus.INSUFFICIENT_HISTORY,
                recencyWeightingPolicy.version(),
                recencyWeightingPolicy.weights(actualYearsUsed),
                rollingWindowKey(mode, requestedYears, featureGroup, fromDate, cutoffDate)
        );
    }

    private int resolveRequestedSeasonCount(Integer requestedSeasonCount) {
        int defaultCount = Math.max(1, predictionProperties.defaultSeasonCount());
        int maximum = Math.max(defaultCount, predictionProperties.maximumSeasonCount());
        int requested = requestedSeasonCount == null ? defaultCount : requestedSeasonCount;
        if (requested < 1) {
            throw new InvalidRequestException("requestedSeasonCount must be at least 1.");
        }
        if (requested > maximum) {
            throw new InvalidRequestException("requestedSeasonCount must be less than or equal to " + maximum + ".");
        }
        return requested;
    }

    private Set<String> discoveredSeasons(League league) {
        Set<String> seasons = externalSourceMappingRepository.findBySourceTypeAndEntityTypeAndLeague_Id(
                        ExternalSourceType.THESPORTSDB,
                        ExternalEntityType.SEASON,
                        league.getId()
                )
                .stream()
                .map(ExternalSourceMapping::getSeason)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (seasons.isEmpty()) {
            seasons.addAll(matchRepository.findDistinctSeasonLabelsByLeagueCode(league.getCode()));
        }
        return seasons.stream()
                .sorted(Comparator.comparingInt(this::seasonSortKey).reversed())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> candidates(
            League league,
            SeasonSelectionMode mode,
            Set<String> customSeasonIds,
            Set<String> discovered,
            Set<String> imported
    ) {
        if (mode == SeasonSelectionMode.CUSTOM) {
            if (customSeasonIds == null || customSeasonIds.isEmpty()) {
                throw new InvalidRequestException("customSeasonIds are required when seasonSelectionMode is CUSTOM.");
            }
            List<String> invalid = customSeasonIds.stream()
                    .filter(season -> !imported.contains(season))
                    .toList();
            if (!invalid.isEmpty()) {
                throw new InvalidRequestException("Requested custom seasons are not imported: " + invalid + ".");
            }
            return customSeasonIds.stream()
                    .sorted(Comparator.comparingInt(this::seasonSortKey).reversed())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        Set<String> source = discovered.isEmpty() ? imported : discovered;
        return source.stream()
                .filter(imported::contains)
                .filter(season -> mode != SeasonSelectionMode.RECENT_COMPLETE_ONLY || !season.equals(league.getCurrentSeason()))
                .sorted(Comparator.comparingInt(this::seasonSortKey).reversed())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private SeasonCandidate seasonCandidate(League league, String season, LocalDate cutoffDate, FeatureGroup featureGroup) {
        int completed = safeInt(matchRepository.countByLeague_CodeAndSeasonLabelAndStatusAndMatchDateLessThanEqual(
                league.getCode(),
                season,
                MatchStatus.FINISHED,
                cutoffDate
        ));
        if (completed < Math.max(1, predictionProperties.minimumCompletedMatchesPerUsableSeason())) {
            return new SeasonCandidate(season, completed, SeasonUsabilityStatus.UNUSABLE, "INSUFFICIENT_RESULTS");
        }
        LeagueSeasonCoverage coverage = coverageRepository.findByLeague_CodeAndSeasonLabel(league.getCode(), season).orElse(null);
        SeasonUsabilityStatus status = usability(featureGroup, coverage, completed);
        return new SeasonCandidate(season, completed, status, coverageLabel(featureGroup, coverage));
    }

    private SeasonUsabilityStatus usability(FeatureGroup featureGroup, LeagueSeasonCoverage coverage, int completedMatches) {
        if (completedMatches <= 0) {
            return SeasonUsabilityStatus.UNUSABLE;
        }
        return switch (featureGroup) {
            case RESULTS, GOALS -> completedMatches >= predictionProperties.minimumCompletedMatchesPerUsableSeason() * 2
                    ? SeasonUsabilityStatus.FULL
                    : SeasonUsabilityStatus.PARTIAL;
            case CORNERS -> coverageLevelUsability(coverage == null ? CoverageLevel.UNAVAILABLE : coverage.getCornersCoverageLevel());
            case CARDS -> coverageLevelUsability(coverage == null ? CoverageLevel.UNAVAILABLE : coverage.getCardsCoverageLevel());
            case PLAYER_GOALS, PLAYER_ASSISTS, PLAYER_PASSES, GOALKEEPER_SAVES -> SeasonUsabilityStatus.UNUSABLE;
        };
    }

    private SeasonUsabilityStatus coverageLevelUsability(CoverageLevel level) {
        if (level == CoverageLevel.FULL) {
            return SeasonUsabilityStatus.FULL;
        }
        if (level == CoverageLevel.PARTIAL) {
            return SeasonUsabilityStatus.PARTIAL;
        }
        if (level == CoverageLevel.SPARSE) {
            return SeasonUsabilityStatus.SPARSE;
        }
        return SeasonUsabilityStatus.UNUSABLE;
    }

    private HistoricalDepthStatus depthStatus(int requested, int actual, boolean currentIncluded) {
        if (actual <= 0) {
            return HistoricalDepthStatus.INSUFFICIENT_HISTORY;
        }
        if (actual == 1 && currentIncluded) {
            return HistoricalDepthStatus.CURRENT_SEASON_ONLY;
        }
        if (actual < requested) {
            return HistoricalDepthStatus.REDUCED_AVAILABLE_DEPTH;
        }
        return HistoricalDepthStatus.FULL_REQUESTED_DEPTH;
    }

    private LocalDate oldestDate(League league, List<String> selected, LocalDate cutoffDate) {
        return matchRepository.findFinishedMatchesForFeatureGenerationWindow(league.getCode(), selected, cutoffDate)
                .stream()
                .map(match -> match.getKickoffAt().toLocalDate())
                .min(LocalDate::compareTo)
                .orElse(null);
    }

    private String coverageLabel(FeatureGroup group, List<SeasonCandidate> candidates) {
        if (candidates.isEmpty()) {
            return group.name() + ":UNUSABLE";
        }
        boolean partial = candidates.stream().anyMatch(candidate -> candidate.usabilityStatus() == SeasonUsabilityStatus.PARTIAL);
        return group.name() + ":" + (partial ? "PARTIAL" : "FULL");
    }

    private String coverageLabel(FeatureGroup group, LeagueSeasonCoverage coverage) {
        if (coverage == null) {
            return group.name() + ":UNKNOWN";
        }
        return switch (group) {
            case CORNERS -> group.name() + ":" + coverage.getCornersCoverageLevel();
            case CARDS -> group.name() + ":" + coverage.getCardsCoverageLevel();
            default -> group.name() + ":" + coverage.getStatisticsCoverageLevel();
        };
    }

    private String seasonWindowKey(SeasonSelectionMode mode, int requested, FeatureGroup group, List<String> selected) {
        String seasons = selected.isEmpty() ? "none" : String.join("_", selected);
        String key = mode.name() + ":" + requested + ":" + group.name() + ":" + seasons;
        return key.length() <= 220 ? key : key.substring(0, 220);
    }

    private String rollingWindowId(int requestedYears, LocalDate fromDate, LocalDate cutoffDate) {
        return ROLLING_YEARS_WINDOW_PREFIX + "_" + requestedYears + "Y_" + fromDate + "_" + cutoffDate;
    }

    private String rollingWindowKey(
            SeasonSelectionMode mode,
            int requestedYears,
            FeatureGroup group,
            LocalDate fromDate,
            LocalDate cutoffDate
    ) {
        String key = mode.name() + ":" + requestedYears + ":" + group.name() + ":"
                + rollingWindowId(requestedYears, fromDate, cutoffDate);
        return key.length() <= 220 ? key : key.substring(0, 220);
    }

    private String rollingWindowCoverageLabel(FeatureGroup group, int completedMatches, int requestedYears) {
        if (completedMatches < Math.max(1, predictionProperties.minimumCompletedMatchesPerUsableSeason())) {
            return group.name() + ":UNUSABLE";
        }
        int fullThreshold = Math.max(
                predictionProperties.minimumCompletedMatchesPerUsableSeason() * 2,
                predictionProperties.minimumCompletedMatchesPerUsableSeason() * requestedYears
        );
        return group.name() + ":" + (completedMatches >= fullThreshold ? "FULL" : "PARTIAL");
    }

    private int seasonSortKey(String season) {
        if (!StringUtils.hasText(season)) {
            return 0;
        }
        Matcher matcher = YEAR_PATTERN.matcher(season);
        int latest = 0;
        while (matcher.find()) {
            latest = Math.max(latest, Integer.parseInt(matcher.group()));
        }
        return latest;
    }

    private int safeInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private record SeasonCandidate(
            String season,
            int completedMatches,
            SeasonUsabilityStatus usabilityStatus,
            String coverageLabel
    ) {
    }
}
