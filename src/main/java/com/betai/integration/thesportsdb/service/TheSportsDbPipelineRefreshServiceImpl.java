package com.betai.integration.thesportsdb.service;

import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.league.CompetitionHistoryPolicy;
import com.betai.domain.source.ExternalEntityType;
import com.betai.domain.source.ExternalMappingStatus;
import com.betai.domain.source.ExternalSourceMapping;
import com.betai.domain.source.ExternalSourceType;
import com.betai.config.PredictionProperties;
import com.betai.exception.InvalidRequestException;
import com.betai.integration.thesportsdb.TheSportsDbProperties;
import com.betai.integration.thesportsdb.dto.TheSportsDbImportSummary;
import com.betai.integration.thesportsdb.dto.TheSportsDbPipelineRefreshSummary;
import com.betai.integration.thesportsdb.service.TheSportsDbLeagueSeasonImportService.SeasonLabelStrategy;
import com.betai.repository.ExternalSourceMappingRepository;
import com.betai.repository.LeagueRepository;
import com.betai.repository.MatchRepository;
import com.betai.repository.SourceTargetRepository;
import com.betai.service.CompetitionHistoryPolicyService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
public class TheSportsDbPipelineRefreshServiceImpl implements TheSportsDbPipelineRefreshService {

    private static final Pattern YEAR_PATTERN = Pattern.compile("\\d{4}");
    private static final Pattern LEAGUE_ID_URL_PATTERN = Pattern.compile("(?:/league/|[?&]id=)(\\d+)");
    private static final List<InternationalHistorySource> WORLD_CUP_HISTORY_SOURCES = List.of(
            new InternationalHistorySource("4429", "FIFA World Cup"),
            new InternationalHistorySource("5513", "World Cup Qualifying AFC"),
            new InternationalHistorySource("5514", "World Cup Qualifying CAF"),
            new InternationalHistorySource("5515", "World Cup Qualifying CONMEBOL"),
            new InternationalHistorySource("5516", "World Cup Qualifying CONCACAF"),
            new InternationalHistorySource("5517", "World Cup Qualifying OFC"),
            new InternationalHistorySource("5518", "World Cup Qualifying UEFA"),
            new InternationalHistorySource("4562", "International Friendlies"),
            new InternationalHistorySource("4502", "UEFA European Championships"),
            new InternationalHistorySource("4866", "AFC Asian Cup"),
            new InternationalHistorySource("4867", "OFC Nations Cup"),
            new InternationalHistorySource("4873", "CONCACAF Gold Cup")
    );

    private final TheSportsDbProperties properties;
    private final PredictionProperties predictionProperties;
    private final LeagueRepository leagueRepository;
    private final ExternalSourceMappingRepository externalSourceMappingRepository;
    private final MatchRepository matchRepository;
    private final SourceTargetRepository sourceTargetRepository;
    private final TheSportsDbLeagueSeasonImportService leagueSeasonImportService;
    private final TheSportsDbCoverageService coverageService;
    private final CompetitionHistoryPolicyService competitionHistoryPolicyService;
    private final ObjectMapper objectMapper;

    @Override
    public TheSportsDbPipelineRefreshSummary refresh(Set<LeagueCode> leagueCodes, Integer requestedSeasonCount) {
        if (!properties.enabled() || !StringUtils.hasText(properties.apiKey()) || leagueCodes == null || leagueCodes.isEmpty()) {
            int requested = leagueCodes == null ? 0 : leagueCodes.size();
            Counters counters = new Counters(requested);
            counters.skippedLeagues = requested;
            counters.unresolvedLeagues = requested;
            if (requested > 0) {
                counters.skipReasons.add("TheSportsDB integration is disabled or the API key is missing.");
            }
            return counters.toSummary();
        }

        int seasonCount = resolveRequestedSeasonCount(requestedSeasonCount);
        Counters counters = new Counters(leagueCodes.size());
        for (LeagueCode leagueCode : leagueCodes) {
            League league = leagueRepository.findByCode(leagueCode).orElse(null);
            if (league == null) {
                counters.skippedLeagues++;
                counters.unresolvedLeagues++;
                counters.skipReasons.add(leagueCode.name() + ": league is not configured.");
                continue;
            }
            ResolvedLeagueId resolvedLeagueId = externalLeagueId(league);
            if (!StringUtils.hasText(resolvedLeagueId.externalLeagueId())) {
                counters.skippedLeagues++;
                counters.unresolvedLeagues++;
                counters.skipReasons.add(leagueCode.name() + ": no active TheSportsDB league mapping or source target leagueId.");
                continue;
            }
            counters.resolvedLeagues++;
            if (resolvedLeagueId.fromSourceTarget()) {
                createOrUpdateLeagueMapping(league, resolvedLeagueId.externalLeagueId());
            }

            try {
                if (competitionHistoryPolicyService.policyFor(leagueCode) == CompetitionHistoryPolicy.INTERNATIONAL_FOUR_YEAR_WINDOW) {
                    importInternationalHistoryWindow(league, counters);
                } else {
                    importRequestedSeasonWindow(league, resolvedLeagueId.externalLeagueId(), seasonCount, counters);
                }
                counters.refreshedLeagues++;
            } catch (RuntimeException exception) {
                counters.failedLeagues++;
                counters.failureReasons.add(leagueCode.name() + ": [" + categorizeException(exception) + "] " + truncate(exception.getMessage(), 300));
            }
        }
        return counters.toSummary();
    }

    private String categorizeException(RuntimeException exception) {
        if (exception instanceof com.betai.integration.thesportsdb.client.TheSportsDbClientException clientEx) {
            int status = clientEx.statusCode();
            if (status == 429) return "RATE_LIMIT";
            if (status == 401 || status == 403) return "AUTH_OR_PERMISSION";
            if (status == 404) return "NOT_FOUND_OR_UNSUPPORTED_ENDPOINT";
            if (status >= 500) return "UPSTREAM_SERVER_ERROR";
            String msg = clientEx.getMessage() != null ? clientEx.getMessage().toLowerCase() : "";
            if (msg.contains("timeout") || msg.contains("interrupted")) return "TIMEOUT";
        }
        if (exception instanceof org.springframework.dao.DataAccessException || (exception.getMessage() != null && exception.getMessage().contains("Query did not return a unique result"))) {
            return "MAPPING_ERROR";
        }
        if (exception.getClass().getName().contains("JsonProcessingException") || (exception.getMessage() != null && (exception.getMessage().contains("json") || exception.getMessage().contains("parse")))) {
            return "RESPONSE_PARSE_ERROR";
        }
        if (exception.getMessage() != null && (exception.getMessage().contains("no data") || exception.getMessage().contains("empty"))) {
            return "NO_DATA_AVAILABLE";
        }
        return "UNKNOWN_ERROR";
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

    private ResolvedLeagueId externalLeagueId(League league) {
        List<String> sourceTargetLeagueIds = sourceTargetRepository.findByLeague_CodeOrderBySourceTypeAscNameAsc(league.getCode())
                .stream()
                .filter(this::isTheSportsDbSourceTarget)
                .map(this::leagueIdFromSourceTarget)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        List<String> mappedLeagueIds = externalSourceMappingRepository
                .findBySourceTypeAndEntityTypeAndLeague_IdAndStatus(
                        ExternalSourceType.THESPORTSDB,
                        ExternalEntityType.LEAGUE,
                        league.getId(),
                        ExternalMappingStatus.RESOLVED
                )
                .stream()
                .map(ExternalSourceMapping::getExternalEntityId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        String mapped = mappedLeagueIds.stream()
                .filter(sourceTargetLeagueIds::contains)
                .findFirst()
                .or(() -> mappedLeagueIds.stream().findFirst())
                .orElse(null);
        if (StringUtils.hasText(mapped)) {
            return new ResolvedLeagueId(mapped, false);
        }
        String sourceTargetLeagueId = sourceTargetLeagueIds.stream().findFirst().orElse(null);
        return new ResolvedLeagueId(sourceTargetLeagueId, StringUtils.hasText(sourceTargetLeagueId));
    }

    private void createOrUpdateLeagueMapping(League league, String externalLeagueId) {
        ExternalSourceMapping mapping = externalSourceMappingRepository
                .findBySourceTypeAndEntityTypeAndExternalEntityId(
                        ExternalSourceType.THESPORTSDB,
                        ExternalEntityType.LEAGUE,
                        externalLeagueId
                )
                .orElseGet(ExternalSourceMapping::new);
        mapping.setSourceType(ExternalSourceType.THESPORTSDB)
                .setEntityType(ExternalEntityType.LEAGUE)
                .setExternalEntityId(externalLeagueId)
                .setInternalEntityId(league.getId())
                .setLeague(league)
                .setSeason(league.getCurrentSeason())
                .setStatus(ExternalMappingStatus.RESOLVED)
                .setExternalName(league.getName())
                .setUnresolvedReason(null);
        externalSourceMappingRepository.save(mapping);
    }

    private boolean isTheSportsDbSourceTarget(com.betai.domain.source.SourceTarget sourceTarget) {
        String name = sourceTarget.getName() == null ? "" : sourceTarget.getName();
        String url = sourceTarget.getUrlTemplate() == null ? "" : sourceTarget.getUrlTemplate();
        String selectors = sourceTarget.getSelectorsJson() == null ? "" : sourceTarget.getSelectorsJson();
        return sourceTarget.isActive()
                && (name.contains("TheSportsDB")
                || url.contains("thesportsdb.com")
                || selectors.toLowerCase().contains("thesportsdb"));
    }

    private String leagueIdFromSourceTarget(com.betai.domain.source.SourceTarget sourceTarget) {
        String fromSelectors = leagueIdFromSelectors(sourceTarget.getSelectorsJson());
        if (StringUtils.hasText(fromSelectors)) {
            return fromSelectors;
        }
        String url = sourceTarget.getUrlTemplate();
        if (!StringUtils.hasText(url)) {
            return null;
        }
        Matcher matcher = LEAGUE_ID_URL_PATTERN.matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String leagueIdFromSelectors(String selectorsJson) {
        if (!StringUtils.hasText(selectorsJson)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(selectorsJson);
            JsonNode leagueId = root.get("leagueId");
            return leagueId == null || leagueId.isNull() ? null : leagueId.asText(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void importRequestedSeasonWindow(
            League league,
            String externalLeagueId,
            int requestedSeasonCount,
            Counters counters
    ) {
        Set<String> importedThisRun = new LinkedHashSet<>();
        Set<String> discovered = discoveredSeasons(league);
        if (discovered.isEmpty()) {
            importOneSeason(league, externalLeagueId, league.getCurrentSeason(), counters);
            importedThisRun.add(league.getCurrentSeason());
            discovered = discoveredSeasons(league);
        }

        List<String> selectedSeasons = selectedSeasons(league, discovered, requestedSeasonCount);
        for (String season : selectedSeasons) {
            if (importedThisRun.contains(season)) {
                continue;
            }
            importOneSeason(league, externalLeagueId, season, counters);
            importedThisRun.add(season);
        }
    }

    private void importInternationalHistoryWindow(League league, Counters counters) {
        int currentYear = seasonSortKey(league.getCurrentSeason());
        if (currentYear <= 0) {
            throw new IllegalStateException("International history window requires a current season year for " + league.getCode() + ".");
        }
        for (InternationalHistorySource source : WORLD_CUP_HISTORY_SOURCES) {
            createOrUpdateLeagueMapping(league, source.externalLeagueId());
            for (int year = currentYear; year > currentYear - CompetitionHistoryPolicyService.INTERNATIONAL_HISTORY_WINDOW_YEARS; year--) {
                importOneSeason(
                        league,
                        source.externalLeagueId(),
                        String.valueOf(year),
                        counters,
                        SeasonLabelStrategy.PRESERVE_REQUESTED_SEASON
                );
            }
        }
    }

    private Set<String> discoveredSeasons(League league) {
        return externalSourceMappingRepository.findBySourceTypeAndEntityTypeAndLeague_Id(
                        ExternalSourceType.THESPORTSDB,
                        ExternalEntityType.SEASON,
                        league.getId()
                )
                .stream()
                .map(ExternalSourceMapping::getSeason)
                .filter(StringUtils::hasText)
                .sorted(Comparator.comparingInt(this::seasonSortKey).reversed())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<String> selectedSeasons(League league, Set<String> discovered, int requestedSeasonCount) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        if (discovered.isEmpty() && StringUtils.hasText(league.getCurrentSeason())) {
            candidates.add(league.getCurrentSeason());
        }
        candidates.addAll(discovered);
        int currentSeasonSortKey = seasonSortKey(league.getCurrentSeason());
        return candidates.stream()
                .filter(season -> currentSeasonSortKey <= 0 || seasonSortKey(season) <= currentSeasonSortKey)
                .sorted(Comparator.comparingInt(this::seasonSortKey).reversed())
                .limit(requestedSeasonCount)
                .toList();
    }

    private void importOneSeason(League league, String externalLeagueId, String season, Counters counters) {
        importOneSeason(league, externalLeagueId, season, counters, null);
    }

    private void importOneSeason(
            League league,
            String externalLeagueId,
            String season,
            Counters counters,
            SeasonLabelStrategy seasonLabelStrategy
    ) {
        counters.requestedSeasons++;
        long existingRows = matchRepository.countByLeague_CodeAndSeasonLabel(league.getCode(), season);
        TheSportsDbImportSummary summary = seasonLabelStrategy == null
                ? leagueSeasonImportService.importLeagueSeason(league.getCode(), externalLeagueId, season)
                : leagueSeasonImportService.importLeagueSeason(
                league.getCode(),
                externalLeagueId,
                season,
                seasonLabelStrategy
        );
        coverageService.recalculate(league.getCode(), season);
        counters.importedSeasons++;
        if (existingRows > 0 && (summary.fixturesCreated() > 0 || summary.fixturesUpdated() > 0)) {
            counters.partiallyRefreshedSeasons++;
        }
        if (existingRows > 0 && summary.fixturesCreated() == 0 && summary.fixturesUpdated() == 0) {
            counters.alreadyCompleteSeasons++;
        }
        counters.fixturesCreated += summary.fixturesCreated();
        counters.fixturesUpdated += summary.fixturesUpdated();
        counters.teamsResolved += summary.teamsResolved();
        counters.teamsCreated += summary.teamsCreated();
        counters.teamsUnresolved += summary.teamsUnresolved();
        counters.apiCallsMade += 3;
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

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record ResolvedLeagueId(String externalLeagueId, boolean fromSourceTarget) {
    }

    private record InternationalHistorySource(String externalLeagueId, String name) {
    }

    private static final class Counters {
        private final int requestedLeagues;
        private int resolvedLeagues;
        private int unresolvedLeagues;
        private int refreshedLeagues;
        private int skippedLeagues;
        private int failedLeagues;
        private int requestedSeasons;
        private int importedSeasons;
        private int alreadyCompleteSeasons;
        private int partiallyRefreshedSeasons;
        private int fixturesCreated;
        private int fixturesUpdated;
        private int duplicateMatchesIgnored;
        private int teamsResolved;
        private int teamsCreated;
        private int teamsUnresolved;
        private int apiCallsMade;
        private final List<String> skipReasons = new ArrayList<>();
        private final List<String> failureReasons = new ArrayList<>();

        private Counters(int requestedLeagues) {
            this.requestedLeagues = requestedLeagues;
        }

        private TheSportsDbPipelineRefreshSummary toSummary() {
            return new TheSportsDbPipelineRefreshSummary(
                    requestedLeagues,
                    resolvedLeagues,
                    unresolvedLeagues,
                    refreshedLeagues,
                    skippedLeagues,
                    failedLeagues,
                    requestedSeasons,
                    importedSeasons,
                    alreadyCompleteSeasons,
                    partiallyRefreshedSeasons,
                    fixturesCreated,
                    fixturesUpdated,
                    duplicateMatchesIgnored,
                    teamsResolved,
                    teamsCreated,
                    teamsUnresolved,
                    apiCallsMade,
                    skipReasons,
                    failureReasons
            );
        }
    }
}
