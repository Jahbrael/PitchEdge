package com.betai.integration.thesportsdb.service;

import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.match.Match;
import com.betai.domain.match.MatchStatus;
import com.betai.domain.source.ExternalEntityType;
import com.betai.domain.source.ExternalMappingStatus;
import com.betai.domain.source.ExternalSourceMapping;
import com.betai.domain.source.ExternalSourceType;
import com.betai.domain.source.RenderMode;
import com.betai.domain.source.SourceTarget;
import com.betai.domain.source.SourceType;
import com.betai.domain.team.Team;
import com.betai.domain.team.TeamAlias;
import com.betai.exception.ReferenceDataNotFoundException;
import com.betai.integration.thesportsdb.TheSportsDbProperties;
import com.betai.integration.thesportsdb.client.TheSportsDbClient;
import com.betai.integration.thesportsdb.client.TheSportsDbClientResponse;
import com.betai.integration.thesportsdb.dto.TheSportsDbEventDto;
import com.betai.integration.thesportsdb.dto.TheSportsDbImportSummary;
import com.betai.integration.thesportsdb.dto.TheSportsDbLeagueDto;
import com.betai.integration.thesportsdb.dto.TheSportsDbSeasonDto;
import com.betai.integration.thesportsdb.dto.TheSportsDbTeamDto;
import com.betai.integration.thesportsdb.mapper.TheSportsDbMapper;
import com.betai.repository.ExternalSourceMappingRepository;
import com.betai.repository.LeagueRepository;
import com.betai.repository.MatchRepository;
import com.betai.repository.SourceTargetRepository;
import com.betai.repository.TeamAliasRepository;
import com.betai.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TheSportsDbLeagueSeasonImportServiceImpl implements TheSportsDbLeagueSeasonImportService {

    private static final String SOURCE_NAME = "thesportsdb-premium-v2";
    private static final String PARSER_VERSION = "thesportsdb-v2-json-v1";

    private final TheSportsDbClient client;
    private final TheSportsDbMapper mapper;
    private final TheSportsDbSnapshotService snapshotService;
    private final ExternalSourceMappingService externalSourceMappingService;
    private final ExternalSourceMappingRepository externalSourceMappingRepository;
    private final LeagueRepository leagueRepository;
    private final SourceTargetRepository sourceTargetRepository;
    private final TeamRepository teamRepository;
    private final TeamAliasRepository teamAliasRepository;
    private final MatchRepository matchRepository;
    private final TheSportsDbProperties properties;

    @Override
    @Transactional
    public TheSportsDbImportSummary importLeagueSeason(
            LeagueCode leagueCode,
            String externalLeagueId,
            String season,
            SeasonLabelStrategy seasonLabelStrategy
    ) {
        if (!StringUtils.hasText(externalLeagueId)) {
            throw new IllegalArgumentException("externalLeagueId is required.");
        }
        League league = leagueRepository.findByCode(leagueCode)
                .orElseThrow(() -> new ReferenceDataNotFoundException("League is not configured: " + leagueCode + "."));
        String seasonLabel = StringUtils.hasText(season) ? season.trim() : league.getCurrentSeason();
        SourceTarget sourceTarget = sourceTarget(league, externalLeagueId.trim(), seasonLabel);
        ImportCounters counters = new ImportCounters();
        Set<String> aliasWriteCache = new HashSet<>();
        Map<String, Optional<Team>> mappedTeamCache = new HashMap<>();

        externalSourceMappingService.markResolved(
                ExternalSourceType.THESPORTSDB,
                ExternalEntityType.LEAGUE,
                externalLeagueId.trim(),
                league.getId(),
                league,
                seasonLabel,
                league.getName()
        );

        importLeagueMetadata(league, sourceTarget, externalLeagueId.trim(), seasonLabel);
        importSeasons(league, sourceTarget, externalLeagueId.trim(), seasonLabel, counters);
        importTeams(league, sourceTarget, externalLeagueId.trim(), seasonLabel, counters, aliasWriteCache, mappedTeamCache);
        importSchedule(league, sourceTarget, externalLeagueId.trim(), seasonLabel, counters, aliasWriteCache, mappedTeamCache, seasonLabelStrategy);

        return new TheSportsDbImportSummary(
                league.getCode(),
                externalLeagueId.trim(),
                seasonLabel,
                counters.seasonsImported,
                counters.teamsResolved,
                counters.teamsCreated,
                counters.teamsUnresolved,
                counters.fixturesCreated,
                counters.fixturesUpdated,
                counters.fixturesSkipped
        );
    }

    private void importLeagueMetadata(
            League league,
            SourceTarget sourceTarget,
            String externalLeagueId,
            String seasonLabel
    ) {
        TheSportsDbClientResponse response = client.lookupLeague(externalLeagueId);
        snapshotService.persist(response, metadata(
                league,
                sourceTarget,
                Map.of("leagueId", externalLeagueId),
                externalLeagueId,
                externalLeagueId,
                seasonLabel,
                null,
                null,
                "RAW_STORED"
        ));
        mapper.leagues(response.rawJson()).stream()
                .filter(sourceLeague -> externalLeagueId.equals(sourceLeague.externalLeagueId()))
                .findFirst()
                .ifPresent(sourceLeague -> saveLeagueArtwork(league, sourceLeague));
    }

    private void importSeasons(
            League league,
            SourceTarget sourceTarget,
            String externalLeagueId,
            String seasonLabel,
            ImportCounters counters
    ) {
        TheSportsDbClientResponse response = client.listSeasons(externalLeagueId);
        snapshotService.persist(response, metadata(
                league,
                sourceTarget,
                Map.of("leagueId", externalLeagueId),
                externalLeagueId,
                externalLeagueId,
                seasonLabel,
                null,
                null,
                "RAW_STORED"
        ));
        List<TheSportsDbSeasonDto> seasons = mapper.seasons(response.rawJson());
        for (TheSportsDbSeasonDto season : seasons) {
            String internalSeason = internalSeasonLabel(season.season());
            externalSourceMappingService.markResolved(
                    ExternalSourceType.THESPORTSDB,
                    ExternalEntityType.SEASON,
                    externalLeagueId + ":" + internalSeason,
                    null,
                    league,
                    internalSeason,
                    internalSeason
            );
            counters.seasonsImported++;
        }
    }

    private void importTeams(
            League league,
            SourceTarget sourceTarget,
            String externalLeagueId,
            String seasonLabel,
            ImportCounters counters,
            Set<String> aliasWriteCache,
            Map<String, Optional<Team>> mappedTeamCache
    ) {
        TheSportsDbClientResponse response = client.listTeams(externalLeagueId);
        snapshotService.persist(response, metadata(
                league,
                sourceTarget,
                Map.of("leagueId", externalLeagueId),
                externalLeagueId,
                externalLeagueId,
                seasonLabel,
                null,
                null,
                "RAW_STORED"
        ));
        boolean allowCreateMissingTeams = teamRepository.countByLeague_Code(league.getCode()) == 0;
        for (TheSportsDbTeamDto sourceTeam : mapper.teams(response.rawJson())) {
            TeamResolution resolution = resolveTeam(league, sourceTeam, allowCreateMissingTeams, aliasWriteCache, mappedTeamCache);
            switch (resolution.status()) {
                case RESOLVED -> counters.teamsResolved++;
                case CREATED -> counters.teamsCreated++;
                case UNRESOLVED -> counters.teamsUnresolved++;
            }
        }
    }

    private void importSchedule(
            League league,
            SourceTarget sourceTarget,
            String externalLeagueId,
            String seasonLabel,
            ImportCounters counters,
            Set<String> aliasWriteCache,
            Map<String, Optional<Team>> mappedTeamCache,
            SeasonLabelStrategy seasonLabelStrategy
    ) {
        String apiSeasonLabel = apiSeasonLabel(seasonLabel);
        TheSportsDbClientResponse response = client.scheduleLeague(externalLeagueId, apiSeasonLabel);
        snapshotService.persist(response, metadata(
                league,
                sourceTarget,
                Map.of("leagueId", externalLeagueId, "season", apiSeasonLabel),
                externalLeagueId + ":" + apiSeasonLabel,
                externalLeagueId,
                seasonLabel,
                null,
                null,
                "RAW_STORED"
        ));
        for (TheSportsDbEventDto event : mapper.events(response.rawJson())) {
            if (event.kickoffAt() == null) {
                counters.fixturesSkipped++;
                continue;
            }
            TeamResolution homeResolution = resolveOrCreateEventTeam(
                    league,
                    event.externalHomeTeamId(),
                    event.homeTeamName(),
                    event.strHomeTeamBadge(),
                    aliasWriteCache,
                    mappedTeamCache
            );
            TeamResolution awayResolution = resolveOrCreateEventTeam(
                    league,
                    event.externalAwayTeamId(),
                    event.awayTeamName(),
                    event.strAwayTeamBadge(),
                    aliasWriteCache,
                    mappedTeamCache
            );
            Team homeTeam = homeResolution.team();
            Team awayTeam = awayResolution.team();
            recordTeamResolution(homeResolution, counters);
            recordTeamResolution(awayResolution, counters);
            if (homeTeam == null || awayTeam == null || homeTeam.equals(awayTeam)) {
                counters.fixturesSkipped++;
                continue;
            }

            Match match = match(league, event, homeTeam, awayTeam);
            boolean created = match.getId() == null;
            applyEvent(match, league, event, seasonLabel, homeTeam, awayTeam, seasonLabelStrategy);
            Match saved = matchRepository.save(match);
            externalSourceMappingService.markResolved(
                    ExternalSourceType.THESPORTSDB,
                    ExternalEntityType.EVENT,
                    event.externalEventId(),
                    saved.getId(),
                    league,
                    seasonLabel,
                    event.homeTeamName() + " vs " + event.awayTeamName()
            );
            externalSourceMappingService.markResolved(
                    ExternalSourceType.THESPORTSDB,
                    ExternalEntityType.FIXTURE,
                    event.externalEventId(),
                    saved.getId(),
                    league,
                    seasonLabel,
                    event.homeTeamName() + " vs " + event.awayTeamName()
            );
            if (created) {
                counters.fixturesCreated++;
            } else {
                counters.fixturesUpdated++;
            }
        }
    }

    private TeamResolution resolveTeam(
            League league,
            TheSportsDbTeamDto sourceTeam,
            boolean allowCreateMissingTeams,
            Set<String> aliasWriteCache,
            Map<String, Optional<Team>> mappedTeamCache
    ) {
        Optional<Team> existingByMapping = mappedTeam(sourceTeam.externalTeamId(), mappedTeamCache);
        if (existingByMapping.isPresent()) {
            Team team = existingByMapping.get();
            saveTeamArtwork(team, sourceTeam);
            saveAliases(league, team, sourceTeam.aliases(), aliasWriteCache);
            externalSourceMappingService.markResolved(
                    ExternalSourceType.THESPORTSDB,
                    ExternalEntityType.TEAM,
                    sourceTeam.externalTeamId(),
                    team.getId(),
                    league,
                    null,
                    sourceTeam.name()
            );
            return new TeamResolution(team, TeamResolutionStatus.RESOLVED);
        }

        Optional<Team> existingByName = sourceTeam.aliases().stream()
                .map(alias -> teamAliasRepository.findByLeague_CodeAndAliasNormalized(league.getCode(), normalizeKey(alias))
                        .map(TeamAlias::getTeam)
                        .or(() -> teamRepository.findByLeague_CodeAndCanonicalNameIgnoreCaseSafely(league.getCode(), alias)))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
        if (existingByName.isPresent()) {
            Team team = existingByName.get();
            saveTeamArtwork(team, sourceTeam);
            saveAliases(league, team, sourceTeam.aliases(), aliasWriteCache);
            externalSourceMappingService.markResolved(
                    ExternalSourceType.THESPORTSDB,
                    ExternalEntityType.TEAM,
                    sourceTeam.externalTeamId(),
                    team.getId(),
                    league,
                    null,
                    sourceTeam.name()
            );
            cacheMappedTeam(sourceTeam.externalTeamId(), team, mappedTeamCache);
            return new TeamResolution(team, TeamResolutionStatus.RESOLVED);
        }

        if (allowCreateMissingTeams) {
            Team created = new Team()
                    .setLeague(league)
                    .setCanonicalName(truncate(sourceTeam.name(), 160))
                    .setShortName(truncate(StringUtils.hasText(sourceTeam.shortName()) ? sourceTeam.shortName() : sourceTeam.name(), 80))
                    .setCountry(truncate(StringUtils.hasText(sourceTeam.country()) ? sourceTeam.country() : league.getCountry(), 128))
                    .setExternalKey(truncate("TSD:" + league.getCode().name() + ":" + sourceTeam.externalTeamId(), 160))
                    .setActive(true);
            applyTeamArtwork(created, sourceTeam);
            created = teamRepository.save(created);
            saveAliases(league, created, sourceTeam.aliases(), aliasWriteCache);
            externalSourceMappingService.markResolved(
                    ExternalSourceType.THESPORTSDB,
                    ExternalEntityType.TEAM,
                    sourceTeam.externalTeamId(),
                    created.getId(),
                    league,
                    null,
                    sourceTeam.name()
            );
            cacheMappedTeam(sourceTeam.externalTeamId(), created, mappedTeamCache);
            return new TeamResolution(created, TeamResolutionStatus.CREATED);
        }

        externalSourceMappingService.markUnresolved(
                ExternalSourceType.THESPORTSDB,
                ExternalEntityType.TEAM,
                sourceTeam.externalTeamId(),
                league,
                null,
                sourceTeam.name(),
                "No canonical team or alias matched."
        );
        return new TeamResolution(null, TeamResolutionStatus.UNRESOLVED);
    }

    private Optional<Team> resolveEventTeam(
            League league,
            String externalTeamId,
            String sourceName,
            Map<String, Optional<Team>> mappedTeamCache
    ) {
        if (StringUtils.hasText(externalTeamId)) {
            Optional<Team> mapped = mappedTeam(externalTeamId, mappedTeamCache);
            if (mapped.isPresent()) {
                return mapped;
            }
        }
        String normalized = normalizeKey(sourceName);
        return teamAliasRepository.findByLeague_CodeAndAliasNormalized(league.getCode(), normalized)
                .map(TeamAlias::getTeam)
                .or(() -> teamRepository.findByLeague_CodeAndCanonicalNameIgnoreCaseSafely(league.getCode(), sourceName));
    }

    private Optional<Team> mappedTeam(String externalTeamId, Map<String, Optional<Team>> mappedTeamCache) {
        if (!StringUtils.hasText(externalTeamId)) {
            return Optional.empty();
        }
        return mappedTeamCache.computeIfAbsent(externalTeamId, this::mappedTeamFromRepository);
    }

    private Optional<Team> mappedTeamFromRepository(String externalTeamId) {
        return externalSourceMappingRepository.findBySourceTypeAndEntityTypeAndExternalEntityId(
                        ExternalSourceType.THESPORTSDB,
                        ExternalEntityType.TEAM,
                        externalTeamId
                )
                .filter(mapping -> mapping.getStatus() == ExternalMappingStatus.RESOLVED)
                .map(ExternalSourceMapping::getInternalEntityId)
                .filter(internalId -> internalId != null)
                .flatMap(teamRepository::findById);
    }

    private Match match(League league, TheSportsDbEventDto event, Team homeTeam, Team awayTeam) {
        Optional<Match> mapped = externalSourceMappingRepository.findBySourceTypeAndEntityTypeAndExternalEntityId(
                        ExternalSourceType.THESPORTSDB,
                        ExternalEntityType.EVENT,
                        event.externalEventId()
                )
                .filter(mapping -> mapping.getStatus() == ExternalMappingStatus.RESOLVED)
                .map(ExternalSourceMapping::getInternalEntityId)
                .filter(internalId -> internalId != null)
                .flatMap(matchRepository::findById);
        String sourceFixtureKey = sourceFixtureKey(event.externalEventId());
        Optional<Match> exactFixtureIdentity = matchRepository
                .findByLeague_CodeAndHomeTeam_IdAndAwayTeam_IdAndKickoffAtSafely(
                        league.getCode(),
                        homeTeam.getId(),
                        awayTeam.getId(),
                        event.kickoffAt()
                );
        if (exactFixtureIdentity.isPresent()) {
            return exactFixtureIdentity.get();
        }
        if (mapped.isPresent()) {
            return mapped.get();
        }
        return matchRepository.findByLeague_CodeAndSourceFixtureKeySafely(league.getCode(), sourceFixtureKey)
                .or(() -> matchRepository.findByLeague_CodeAndHomeTeam_IdAndAwayTeam_IdAndMatchDateSafely(
                        league.getCode(),
                        homeTeam.getId(),
                        awayTeam.getId(),
                        event.kickoffAt().toLocalDate()
                ))
                .orElseGet(Match::new);
    }

    private void applyEvent(
            Match match,
            League league,
            TheSportsDbEventDto event,
            String seasonLabel,
            Team homeTeam,
            Team awayTeam,
            SeasonLabelStrategy seasonLabelStrategy
    ) {
        MatchStatus status = event.status();
        Integer homeScore = event.homeScore();
        Integer awayScore = event.awayScore();
        if (status == MatchStatus.SCHEDULED
                && match.getStatus() == MatchStatus.FINISHED
                && match.getHomeScore() != null
                && match.getAwayScore() != null) {
            status = MatchStatus.FINISHED;
            homeScore = match.getHomeScore();
            awayScore = match.getAwayScore();
        }

        String sourceFixtureKey = StringUtils.hasText(match.getSourceFixtureKey())
                ? match.getSourceFixtureKey()
                : sourceFixtureKey(event.externalEventId());
        match.setLeague(league)
                .setHomeTeam(homeTeam)
                .setAwayTeam(awayTeam)
                .setMatchDate(event.kickoffAt().toLocalDate())
                .setKickoffAt(event.kickoffAt())
                .setStatus(status)
                .setHomeScore(homeScore)
                .setAwayScore(awayScore)
                .setHomeHalfTimeScore(event.homeHalfTimeScore())
                .setAwayHalfTimeScore(event.awayHalfTimeScore())
                .setReferee(StringUtils.hasText(event.referee()) ? truncate(event.referee(), 160) : match.getReferee())
                .setSeasonLabel(truncate(resolvedSeasonLabel(event, seasonLabel, seasonLabelStrategy), 32))
                .setRoundLabel(StringUtils.hasText(event.roundLabel()) ? truncate(event.roundLabel(), 64) : match.getRoundLabel())
                .setVenue(StringUtils.hasText(event.venue()) ? truncate(event.venue(), 160) : match.getVenue())
                .setSourceFixtureKey(sourceFixtureKey);
    }

    private String resolvedSeasonLabel(
            TheSportsDbEventDto event,
            String requestedSeasonLabel,
            SeasonLabelStrategy seasonLabelStrategy
    ) {
        if (seasonLabelStrategy == SeasonLabelStrategy.PRESERVE_REQUESTED_SEASON) {
            return requestedSeasonLabel;
        }
        return StringUtils.hasText(event.season()) ? internalSeasonLabel(event.season()) : requestedSeasonLabel;
    }

    private TeamResolution resolveOrCreateEventTeam(
            League league,
            String externalTeamId,
            String sourceName,
            String badgeUrl,
            Set<String> aliasWriteCache,
            Map<String, Optional<Team>> mappedTeamCache
    ) {
        Optional<Team> existing = resolveEventTeam(league, externalTeamId, sourceName, mappedTeamCache);
        if (existing.isPresent()) {
            saveEventTeamArtwork(existing.get(), badgeUrl);
            if (StringUtils.hasText(externalTeamId)) {
                externalSourceMappingService.markResolved(
                        ExternalSourceType.THESPORTSDB,
                        ExternalEntityType.TEAM,
                        externalTeamId,
                        existing.get().getId(),
                        league,
                        null,
                        sourceName
                );
                cacheMappedTeam(externalTeamId, existing.get(), mappedTeamCache);
            }
            saveAliases(league, existing.get(), eventAliases(sourceName), aliasWriteCache);
            return new TeamResolution(existing.get(), TeamResolutionStatus.RESOLVED);
        }
        if (!StringUtils.hasText(sourceName)) {
            return new TeamResolution(null, TeamResolutionStatus.UNRESOLVED);
        }

        String teamKey = StringUtils.hasText(externalTeamId) ? externalTeamId : normalizeKey(sourceName);
        Team created = teamRepository.save(new Team()
                .setLeague(league)
                .setCanonicalName(truncate(sourceName.trim(), 160))
                .setShortName(truncate(sourceName.trim(), 80))
                .setCountry(truncate(league.getCountry(), 128))
                .setExternalKey(truncate("TSD:" + league.getCode().name() + ":" + teamKey, 160))
                .setBadgeUrl(truncate(nonBlank(badgeUrl), 1000))
                .setActive(true));
        saveAliases(league, created, eventAliases(sourceName), aliasWriteCache);
        if (StringUtils.hasText(externalTeamId)) {
            externalSourceMappingService.markResolved(
                    ExternalSourceType.THESPORTSDB,
                    ExternalEntityType.TEAM,
                    externalTeamId,
                    created.getId(),
                    league,
                    null,
                        sourceName
                );
            cacheMappedTeam(externalTeamId, created, mappedTeamCache);
        }
        return new TeamResolution(created, TeamResolutionStatus.CREATED);
    }

    private void saveTeamArtwork(Team team, TheSportsDbTeamDto sourceTeam) {
        if (applyTeamArtwork(team, sourceTeam)) {
            teamRepository.save(team);
        }
    }

    private void saveLeagueArtwork(League league, TheSportsDbLeagueDto sourceLeague) {
        boolean changed = false;
        changed |= setIfPresent(league::getBadgeUrl, league::setBadgeUrl, sourceLeague.strBadge());
        changed |= setIfPresent(league::getLogoUrl, league::setLogoUrl, sourceLeague.strLogo());
        changed |= setIfPresent(league::getBannerUrl, league::setBannerUrl, sourceLeague.strBanner());
        changed |= setIfPresent(league::getPosterUrl, league::setPosterUrl, sourceLeague.strPoster());
        changed |= setIfPresent(league::getTrophyUrl, league::setTrophyUrl, sourceLeague.strTrophy());
        changed |= setIfPresent(league::getFanartUrl, league::setFanartUrl, sourceLeague.strFanart1());
        if (changed) {
            leagueRepository.save(league);
        }
    }

    private boolean applyTeamArtwork(Team team, TheSportsDbTeamDto sourceTeam) {
        boolean changed = false;
        changed |= setIfPresent(team::getBadgeUrl, team::setBadgeUrl, sourceTeam.strBadge());
        changed |= setIfPresent(team::getLogoUrl, team::setLogoUrl, sourceTeam.strLogo());
        changed |= setIfPresent(team::getBannerUrl, team::setBannerUrl, sourceTeam.strBanner());
        changed |= setIfPresent(team::getEquipmentUrl, team::setEquipmentUrl, sourceTeam.strEquipment());
        changed |= setIfPresent(team::getFanartUrl, team::setFanartUrl, sourceTeam.strFanart1());
        return changed;
    }

    private void saveEventTeamArtwork(Team team, String badgeUrl) {
        if (setIfPresent(team::getBadgeUrl, team::setBadgeUrl, badgeUrl)) {
            teamRepository.save(team);
        }
    }

    private boolean setIfPresent(java.util.function.Supplier<String> getter, java.util.function.Consumer<String> setter, String candidate) {
        String value = nonBlank(candidate);
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String truncated = truncate(value, 1000);
        if (truncated.equals(getter.get())) {
            return false;
        }
        setter.accept(truncated);
        return true;
    }

    private String nonBlank(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void cacheMappedTeam(String externalTeamId, Team team, Map<String, Optional<Team>> mappedTeamCache) {
        if (StringUtils.hasText(externalTeamId) && team != null) {
            mappedTeamCache.put(externalTeamId, Optional.of(team));
        }
    }

    private void recordTeamResolution(TeamResolution resolution, ImportCounters counters) {
        switch (resolution.status()) {
            case RESOLVED -> {
            }
            case CREATED -> counters.teamsCreated++;
            case UNRESOLVED -> counters.teamsUnresolved++;
        }
    }

    private List<String> eventAliases(String sourceName) {
        return StringUtils.hasText(sourceName) ? List.of(sourceName) : List.of();
    }

    private void saveAliases(League league, Team team, List<String> aliases, Set<String> aliasWriteCache) {
        for (String alias : aliases) {
            String normalized = normalizeKey(alias);
            String cacheKey = league.getId() + ":" + normalized;
            if (!aliasWriteCache.add(cacheKey)) {
                continue;
            }
            teamAliasRepository.findByLeague_CodeAndAliasNormalized(league.getCode(), normalized)
                    .orElseGet(() -> teamAliasRepository.save(new TeamAlias()
                            .setLeague(league)
                            .setTeam(team)
                            .setAlias(truncate(alias.trim(), 160))
                            .setAliasNormalized(truncate(normalized, 180))
                            .setSourceName(SOURCE_NAME)));
        }
    }

    private SourceTarget sourceTarget(League league, String externalLeagueId, String seasonLabel) {
        String name = "TheSportsDB Premium V2 " + seasonLabel + " " + league.getName() + " Match Data JSON";
        String apiSeasonLabel = apiSeasonLabel(seasonLabel);
        SourceTarget target = sourceTargetRepository
                .findByLeague_CodeAndSourceTypeAndName(league.getCode(), SourceType.MATCH_DATA, name)
                .orElseGet(SourceTarget::new);
        target.setLeague(league)
                .setSourceType(SourceType.MATCH_DATA)
                .setName(name)
                .setUrlTemplate(baseUrl() + "/schedule/league/" + externalLeagueId + "/" + apiSeasonLabel)
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
                .setSelectorsJson("{\"format\":\"thesportsdb-v2-json\","
                        + "\"provider\":\"thesportsdb-premium\","
                        + "\"leagueId\":\"" + externalLeagueId + "\","
                        + "\"season\":\"" + seasonLabel + "\","
                        + "\"apiSeason\":\"" + apiSeasonLabel + "\"}");
        return sourceTargetRepository.save(target);
    }

    private String apiSeasonLabel(String seasonLabel) {
        return seasonLabel == null ? "" : seasonLabel.trim().replace("/", "-");
    }

    private String internalSeasonLabel(String seasonLabel) {
        String normalized = seasonLabel == null ? "" : seasonLabel.trim();
        if (normalized.matches("\\d{4}-\\d{4}")) {
            return normalized.replace("-", "/");
        }
        return normalized;
    }

    private TheSportsDbSnapshotMetadata metadata(
            League league,
            SourceTarget sourceTarget,
            Map<String, String> requestParameters,
            String externalEntityId,
            String externalLeagueId,
            String season,
            String externalFixtureId,
            String externalEventId,
            String processingStatus
    ) {
        return new TheSportsDbSnapshotMetadata(
                league,
                sourceTarget,
                requestParameters,
                externalEntityId,
                externalLeagueId,
                season,
                externalFixtureId,
                externalEventId,
                PARSER_VERSION,
                processingStatus,
                null
        );
    }

    private String sourceFixtureKey(String externalEventId) {
        return truncate("TSD:" + externalEventId, 180);
    }

    private String baseUrl() {
        return StringUtils.hasText(properties.baseUrl())
                ? properties.baseUrl().trim().replaceAll("/+$", "")
                : "https://www.thesportsdb.com/api/v2/json";
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

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private enum TeamResolutionStatus {
        RESOLVED,
        CREATED,
        UNRESOLVED
    }

    private record TeamResolution(Team team, TeamResolutionStatus status) {
    }

    private static final class ImportCounters {
        private int seasonsImported;
        private int teamsResolved;
        private int teamsCreated;
        private int teamsUnresolved;
        private int fixturesCreated;
        private int fixturesUpdated;
        private int fixturesSkipped;
    }
}
