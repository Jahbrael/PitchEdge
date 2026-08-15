package com.betai.integration.thesportsdb.service;

import com.betai.domain.league.League;
import com.betai.domain.match.Match;
import com.betai.domain.snapshot.RawSnapshot;
import com.betai.domain.source.ExternalEntityType;
import com.betai.domain.source.ExternalMappingStatus;
import com.betai.domain.source.ExternalSourceMapping;
import com.betai.domain.source.ExternalSourceType;
import com.betai.domain.source.RenderMode;
import com.betai.domain.source.SourceTarget;
import com.betai.domain.source.SourceType;
import com.betai.domain.statistics.EventStatistic;
import com.betai.domain.statistics.MatchStatistics;
import com.betai.domain.team.Team;
import com.betai.exception.ReferenceDataNotFoundException;
import com.betai.integration.thesportsdb.TheSportsDbProperties;
import com.betai.integration.thesportsdb.client.TheSportsDbClient;
import com.betai.integration.thesportsdb.client.TheSportsDbClientResponse;
import com.betai.integration.thesportsdb.dto.TheSportsDbEventStatisticDto;
import com.betai.integration.thesportsdb.dto.TheSportsDbEventStatisticsImportSummary;
import com.betai.integration.thesportsdb.mapper.TheSportsDbMapper;
import com.betai.repository.EventStatisticRepository;
import com.betai.repository.ExternalSourceMappingRepository;
import com.betai.repository.MatchRepository;
import com.betai.repository.MatchStatisticsRepository;
import com.betai.repository.SourceTargetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TheSportsDbEventEnrichmentServiceImpl implements TheSportsDbEventEnrichmentService {

    private static final String PARSER_VERSION = "thesportsdb-v2-json-v1";

    private final TheSportsDbClient client;
    private final TheSportsDbMapper mapper;
    private final TheSportsDbSnapshotService snapshotService;
    private final ExternalSourceMappingRepository externalSourceMappingRepository;
    private final MatchRepository matchRepository;
    private final SourceTargetRepository sourceTargetRepository;
    private final EventStatisticRepository eventStatisticRepository;
    private final MatchStatisticsRepository matchStatisticsRepository;
    private final TheSportsDbProperties properties;

    @Override
    @Transactional
    public TheSportsDbEventStatisticsImportSummary importEventStatistics(String externalEventId) {
        Match match = match(externalEventId);
        return importEventStatistics(match, externalEventId, false);
    }

    @Override
    @Transactional
    public TheSportsDbEventStatisticsImportSummary importEventStatisticsForMatch(UUID matchId, String externalEventId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ReferenceDataNotFoundException("Match not found for event statistics import."));
        return importEventStatistics(match, externalEventId, true);
    }

    private TheSportsDbEventStatisticsImportSummary importEventStatistics(
            Match match,
            String externalEventId,
            boolean ensureMapping
    ) {
        if (!StringUtils.hasText(externalEventId)) {
            throw new ReferenceDataNotFoundException("TheSportsDB event id is required for event statistics import.");
        }
        String normalizedExternalEventId = externalEventId.trim();
        if (ensureMapping) {
            ensureEventMapping(match, normalizedExternalEventId);
        }
        League league = match.getLeague();
        SourceTarget sourceTarget = sourceTarget(league, match.getSeasonLabel());
        TheSportsDbClientResponse response = client.lookupEventStats(normalizedExternalEventId);
        RawSnapshot snapshot = snapshotService.persist(response, new TheSportsDbSnapshotMetadata(
                league,
                sourceTarget,
                Map.of("eventId", normalizedExternalEventId),
                normalizedExternalEventId,
                null,
                match.getSeasonLabel(),
                normalizedExternalEventId,
                normalizedExternalEventId,
                PARSER_VERSION,
                "RAW_STORED",
                null
        ));

        List<TheSportsDbEventStatisticDto> sourceStats = mapper.eventStatistics(
                response.rawJson(),
                match.getHomeTeam().getCanonicalName(),
                match.getAwayTeam().getCanonicalName()
        );
        int imported = 0;
        for (TheSportsDbEventStatisticDto sourceStat : sourceStats) {
            Team team = team(match, sourceStat.teamName());
            EventStatistic statistic = eventStatisticRepository.findTheSportsDbStatistic(
                    match.getId(),
                    team == null ? null : team.getId(),
                    sourceStat.statisticCode(),
                    sourceStat.period()
            ).orElseGet(EventStatistic::new);
            statistic.setMatch(match)
                    .setTeam(team)
                    .setRawSnapshot(snapshot)
                    .setStatisticCode(truncate(sourceStat.statisticCode(), 64))
                    .setStatisticName(truncate(sourceStat.statisticName(), 120))
                    .setNumericValue(sourceStat.numericValue())
                    .setTextValue(truncate(sourceStat.textValue(), 240))
                    .setPeriod(truncate(sourceStat.period(), 32))
                    .setSourceType(ExternalSourceType.THESPORTSDB)
                    .setSourceStatisticName(truncate(sourceStat.sourceStatisticName(), 160))
                    .setRetrievedAt(response.retrievedAt());
            eventStatisticRepository.save(statistic);
            imported++;
        }
        int fixedUpdated = updateFixedMatchStatistics(match, snapshot, sourceStats);
        return new TheSportsDbEventStatisticsImportSummary(normalizedExternalEventId, imported, fixedUpdated);
    }

    private void ensureEventMapping(Match match, String externalEventId) {
        ExternalSourceMapping mapping = externalSourceMappingRepository.findBySourceTypeAndEntityTypeAndExternalEntityId(
                ExternalSourceType.THESPORTSDB,
                ExternalEntityType.EVENT,
                externalEventId
        ).orElseGet(ExternalSourceMapping::new);

        if (mapping.getInternalEntityId() != null && !mapping.getInternalEntityId().equals(match.getId())) {
            return;
        }

        mapping.setSourceType(ExternalSourceType.THESPORTSDB)
                .setEntityType(ExternalEntityType.EVENT)
                .setExternalEntityId(externalEventId)
                .setInternalEntityId(match.getId())
                .setLeague(match.getLeague())
                .setSeason(match.getSeasonLabel())
                .setStatus(ExternalMappingStatus.RESOLVED)
                .setExternalName(match.getHomeTeam().getCanonicalName() + " vs " + match.getAwayTeam().getCanonicalName())
                .setUnresolvedReason(null);
        externalSourceMappingRepository.save(mapping);
    }

    private Match match(String externalEventId) {
        return externalSourceMappingRepository.findBySourceTypeAndEntityTypeAndExternalEntityId(
                        ExternalSourceType.THESPORTSDB,
                        ExternalEntityType.EVENT,
                        externalEventId
                )
                .filter(mapping -> mapping.getStatus() == ExternalMappingStatus.RESOLVED)
                .map(ExternalSourceMapping::getInternalEntityId)
                .filter(internalId -> internalId != null)
                .flatMap(matchRepository::findById)
                .orElseThrow(() -> new ReferenceDataNotFoundException(
                        "No BetAI match mapping exists for TheSportsDB event " + externalEventId + "."
                ));
    }

    private int updateFixedMatchStatistics(
            Match match,
            RawSnapshot snapshot,
            List<TheSportsDbEventStatisticDto> sourceStats
    ) {
        MatchStatistics statistics = matchStatisticsRepository.findByMatch_Id(match.getId()).orElseGet(MatchStatistics::new);
        boolean updated = false;
        for (TheSportsDbEventStatisticDto sourceStat : sourceStats) {
            if (sourceStat.numericValue() == null) {
                continue;
            }
            Integer value = sourceStat.numericValue().intValue();
            Team team = team(match, sourceStat.teamName());
            boolean home = team != null && team.equals(match.getHomeTeam());
            boolean away = team != null && team.equals(match.getAwayTeam());
            if (!home && !away) {
                continue;
            }
            switch (sourceStat.statisticCode()) {
                case "SHOTS" -> {
                    if (home) statistics.setHomeShots(value); else statistics.setAwayShots(value);
                    updated = true;
                }
                case "SHOTS_ON_TARGET" -> {
                    if (home) statistics.setHomeShotsOnTarget(value); else statistics.setAwayShotsOnTarget(value);
                    updated = true;
                }
                case "FOULS" -> {
                    if (home) statistics.setHomeFouls(value); else statistics.setAwayFouls(value);
                    updated = true;
                }
                case "CORNERS" -> {
                    if (home) statistics.setHomeCorners(value); else statistics.setAwayCorners(value);
                    updated = true;
                }
                case "YELLOW_CARDS" -> {
                    if (home) statistics.setHomeYellowCards(value); else statistics.setAwayYellowCards(value);
                    updated = true;
                }
                case "RED_CARDS" -> {
                    if (home) statistics.setHomeRedCards(value); else statistics.setAwayRedCards(value);
                    updated = true;
                }
                case "EXPECTED_GOALS" -> {
                    if (home) statistics.setHomeExpectedGoals(sourceStat.numericValue()); else statistics.setAwayExpectedGoals(sourceStat.numericValue());
                    updated = true;
                }
                case "POSSESSION" -> {
                    if (home) statistics.setHomePossession(value); else statistics.setAwayPossession(value);
                    updated = true;
                }
                default -> {
                }
            }
        }
        if (!updated) {
            return 0;
        }
        statistics.setMatch(match).setRawSnapshot(snapshot);
        matchStatisticsRepository.save(statistics);
        return 1;
    }

    private Team team(Match match, String sourceTeamName) {
        if (!StringUtils.hasText(sourceTeamName)) {
            return null;
        }
        String normalized = normalizeKey(sourceTeamName);
        if (normalizeKey(match.getHomeTeam().getCanonicalName()).equals(normalized)) {
            return match.getHomeTeam();
        }
        if (normalizeKey(match.getAwayTeam().getCanonicalName()).equals(normalized)) {
            return match.getAwayTeam();
        }
        return null;
    }

    private SourceTarget sourceTarget(League league, String seasonLabel) {
        String name = "TheSportsDB Premium V2 " + seasonLabel + " " + league.getName() + " Event Stats JSON";
        SourceTarget target = sourceTargetRepository
                .findByLeague_CodeAndSourceTypeAndName(league.getCode(), SourceType.MATCH_DATA, name)
                .orElseGet(SourceTarget::new);
        target.setLeague(league)
                .setSourceType(SourceType.MATCH_DATA)
                .setName(name)
                .setUrlTemplate(baseUrl() + "/lookup/event_stats/{eventId}")
                .setSourceSeasonToken(seasonLabel)
                .setTargetSeasonLabel(seasonLabel)
                .setRenderMode(RenderMode.STATIC_HTML)
                .setRobotsTxtRequired(false)
                .setUserAgent("BetAIResearchBot/0.1 (+local-development)")
                .setRateLimitPerMinute(80)
                .setTimeoutMs(30000)
                .setReliabilityScore(new BigDecimal("95.00"))
                .setFallbackPriority(5)
                .setSystemDisabled(false)
                .setActive(properties.enabled() && StringUtils.hasText(properties.apiKey()))
                .setSelectorsJson("{\"format\":\"thesportsdb-v2-event-stats-json\","
                        + "\"provider\":\"thesportsdb-premium\"}");
        return sourceTargetRepository.save(target);
    }

    private String normalizeKey(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replace("&", " and ")
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return StringUtils.hasText(normalized) ? normalized : "unknown";
    }

    private String baseUrl() {
        return StringUtils.hasText(properties.baseUrl())
                ? properties.baseUrl().trim().replaceAll("/+$", "")
                : "https://www.thesportsdb.com/api/v2/json";
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
