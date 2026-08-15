package com.betai.integration.thesportsdb.service;

import com.betai.api.dto.TheSportsDbArtworkBackfillRequest;
import com.betai.api.dto.TheSportsDbArtworkBackfillResponse;
import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.source.ExternalEntityType;
import com.betai.domain.source.ExternalMappingStatus;
import com.betai.domain.source.ExternalSourceMapping;
import com.betai.domain.source.ExternalSourceType;
import com.betai.domain.source.SourceTarget;
import com.betai.domain.team.Team;
import com.betai.domain.team.TeamAlias;
import com.betai.integration.thesportsdb.client.TheSportsDbClient;
import com.betai.integration.thesportsdb.dto.TheSportsDbLeagueDto;
import com.betai.integration.thesportsdb.dto.TheSportsDbTeamDto;
import com.betai.integration.thesportsdb.mapper.TheSportsDbMapper;
import com.betai.repository.ExternalSourceMappingRepository;
import com.betai.repository.LeagueRepository;
import com.betai.repository.SourceTargetRepository;
import com.betai.repository.TeamAliasRepository;
import com.betai.repository.TeamRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class TheSportsDbArtworkBackfillServiceImpl implements TheSportsDbArtworkBackfillService {

    private static final Pattern LEAGUE_ID_URL_PATTERN = Pattern.compile("(?:/league/|[?&]id=)(\\d+)");

    private final TheSportsDbClient client;
    private final TheSportsDbMapper mapper;
    private final LeagueRepository leagueRepository;
    private final TeamRepository teamRepository;
    private final TeamAliasRepository teamAliasRepository;
    private final ExternalSourceMappingRepository externalSourceMappingRepository;
    private final SourceTargetRepository sourceTargetRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    @Transactional
    public TheSportsDbArtworkBackfillResponse backfillArtwork(TheSportsDbArtworkBackfillRequest request) {
        OffsetDateTime startedAt = OffsetDateTime.now(clock);
        Counters counters = new Counters();
        boolean dryRun = request != null && Boolean.TRUE.equals(request.dryRun());
        boolean teamsOnly = request != null && Boolean.TRUE.equals(request.teamsOnly());
        boolean leaguesOnly = request != null && Boolean.TRUE.equals(request.leaguesOnly());
        Integer limit = request != null && request.limit() != null && request.limit() > 0 ? request.limit() : null;
        String teamFilter = request != null && StringUtils.hasText(request.teamExternalKey()) ? request.teamExternalKey().trim() : null;

        List<League> leagues = leaguesToBackfill(request);

        for (League league : leagues) {
            if (limitReached(counters, limit, teamsOnly, leaguesOnly)) {
                break;
            }
            counters.checkedLeagues++;
            String externalLeagueId = externalLeagueId(league);
            if (!StringUtils.hasText(externalLeagueId)) {
                counters.skippedLeagues++;
                counters.skipReasons.add(league.getCode().name() + ": no active TheSportsDB league mapping or source target leagueId.");
                continue;
            }

            try {
                if (!teamsOnly && teamFilter == null) {
                    TheSportsDbLeagueDto sourceLeague = lookupLeague(externalLeagueId);
                    if (sourceLeague == null) {
                        counters.skippedLeagues++;
                        counters.skipReasons.add(league.getCode().name() + ": TheSportsDB league lookup returned no league details for ID " + externalLeagueId + ".");
                    } else if (applyLeagueArtwork(league, sourceLeague, dryRun)) {
                        if (!dryRun) {
                            leagueRepository.save(league);
                        }
                        counters.updatedLeagues++;
                    } else {
                        counters.unchangedLeagues++;
                    }
                } else {
                    counters.skippedLeagues++;
                }

                if (!leaguesOnly) {
                    if (!limitReached(counters, limit, teamsOnly, leaguesOnly)) {
                        backfillTeams(league, externalLeagueId, counters, dryRun, limit, teamFilter, teamsOnly, leaguesOnly);
                    }
                }
            } catch (RuntimeException exception) {
                counters.failedLeagues++;
                counters.failureReasons.add(league.getCode().name() + ": " + truncate(safeMessage(exception), 300));
            }
        }

        return counters.toResponse(startedAt, OffsetDateTime.now(clock));
    }

    private List<League> leaguesToBackfill(TheSportsDbArtworkBackfillRequest request) {
        Set<LeagueCode> codes = new LinkedHashSet<>();
        if (request != null && request.leagueCodes() != null) {
            codes.addAll(request.leagueCodes());
        }
        if (request != null && request.leagueCode() != null) {
            codes.add(request.leagueCode());
        }
        if (codes.isEmpty() && request != null && StringUtils.hasText(request.teamExternalKey())) {
            String filter = request.teamExternalKey().trim();
            Optional<Team> mappedTeam = externalSourceMappingRepository.findBySourceTypeAndEntityTypeAndExternalEntityId(
                            ExternalSourceType.THESPORTSDB,
                            ExternalEntityType.TEAM,
                            filter
                    )
                    .filter(mapping -> mapping.getStatus() == ExternalMappingStatus.RESOLVED)
                    .map(ExternalSourceMapping::getInternalEntityId)
                    .flatMap(teamRepository::findById);
            mappedTeam.map(Team::getLeague)
                    .map(League::getCode)
                    .ifPresent(codes::add);
            for (League league : leagueRepository.findByActiveTrueOrderByNameAsc()) {
                boolean match = teamRepository.findByLeague_CodeAndActiveTrueOrderByCanonicalNameAsc(league.getCode())
                        .stream()
                        .anyMatch(t -> (t.getExternalKey() != null && t.getExternalKey().equalsIgnoreCase(filter))
                                || (t.getCanonicalName() != null && t.getCanonicalName().equalsIgnoreCase(filter))
                                || (t.getShortName() != null && t.getShortName().equalsIgnoreCase(filter)));
                if (match) {
                    codes.add(league.getCode());
                }
            }
        }
        if (codes.isEmpty()) {
            return leagueRepository.findByActiveTrueOrderByNameAsc();
        }
        return leagueRepository.findByCodeInAndActiveTrue(codes)
                .stream()
                .sorted(Comparator.comparing(League::getName))
                .toList();
    }

    private void backfillTeams(
            League league,
            String externalLeagueId,
            Counters counters,
            boolean dryRun,
            Integer limit,
            String teamFilter,
            boolean teamsOnly,
            boolean leaguesOnly
    ) {
        Map<String, Optional<Team>> mappedTeamCache = new HashMap<>();
        for (TheSportsDbTeamDto sourceTeam : mapper.teams(client.listTeams(externalLeagueId).rawJson())) {
            if (limitReached(counters, limit, teamsOnly || teamFilter != null, leaguesOnly)) {
                break;
            }
            counters.checkedTeams++;
            try {
                Optional<Team> team = resolveTeam(league, sourceTeam, mappedTeamCache);
                if (team.isEmpty()) {
                    counters.skippedTeams++;
                    counters.skipReasons.add(league.getCode().name() + "/" + sourceTeam.name() + ": no matching local team.");
                    continue;
                }
                if (teamFilter != null && !matchesTeamFilter(team.get(), sourceTeam, teamFilter)) {
                    counters.skippedTeams++;
                    continue;
                }
                if (applyTeamArtwork(team.get(), sourceTeam, dryRun)) {
                    if (!dryRun) {
                        teamRepository.save(team.get());
                    }
                    counters.updatedTeams++;
                } else {
                    counters.unchangedTeams++;
                }
            } catch (RuntimeException exception) {
                counters.failedTeams++;
                counters.failureReasons.add(league.getCode().name() + "/" + sourceTeam.name() + ": " + truncate(safeMessage(exception), 300));
            }
        }
    }

    private boolean matchesTeamFilter(Team team, TheSportsDbTeamDto sourceTeam, String filter) {
        if (filter == null) return true;
        if (sourceTeam.externalTeamId() != null && sourceTeam.externalTeamId().equalsIgnoreCase(filter)) return true;
        if (sourceTeam.name() != null && sourceTeam.name().equalsIgnoreCase(filter)) return true;
        if (sourceTeam.aliases().stream().anyMatch(alias -> alias.equalsIgnoreCase(filter))) return true;
        if (team.getExternalKey() != null && team.getExternalKey().equalsIgnoreCase(filter)) return true;
        if (team.getCanonicalName() != null && team.getCanonicalName().equalsIgnoreCase(filter)) return true;
        if (team.getShortName() != null && team.getShortName().equalsIgnoreCase(filter)) return true;
        return false;
    }

    private TheSportsDbLeagueDto lookupLeague(String externalLeagueId) {
        List<TheSportsDbLeagueDto> leagues = mapper.leagues(client.lookupLeague(externalLeagueId).rawJson());
        return leagues.stream()
                .filter(league -> externalLeagueId.equals(league.externalLeagueId()))
                .findFirst()
                .or(() -> leagues.stream().findFirst())
                .orElse(null);
    }

    private Optional<Team> resolveTeam(League league, TheSportsDbTeamDto sourceTeam, Map<String, Optional<Team>> mappedTeamCache) {
        Optional<Team> mapped = mappedTeam(sourceTeam.externalTeamId(), mappedTeamCache);
        if (mapped.isPresent()) {
            return mapped;
        }
        return sourceTeam.aliases().stream()
                .map(alias -> teamAliasRepository.findByLeague_CodeAndAliasNormalized(league.getCode(), normalizeKey(alias))
                        .map(TeamAlias::getTeam)
                        .or(() -> teamRepository.findByLeague_CodeAndCanonicalNameIgnoreCaseSafely(league.getCode(), alias)))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
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

    private String externalLeagueId(League league) {
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
        return mappedLeagueIds.stream()
                .filter(sourceTargetLeagueIds::contains)
                .findFirst()
                .or(() -> mappedLeagueIds.stream().findFirst())
                .or(() -> sourceTargetLeagueIds.stream().findFirst())
                .orElse(null);
    }

    private boolean isTheSportsDbSourceTarget(SourceTarget sourceTarget) {
        String name = sourceTarget.getName() == null ? "" : sourceTarget.getName();
        String url = sourceTarget.getUrlTemplate() == null ? "" : sourceTarget.getUrlTemplate();
        String selectors = sourceTarget.getSelectorsJson() == null ? "" : sourceTarget.getSelectorsJson();
        return sourceTarget.isActive()
                && (name.contains("TheSportsDB")
                || url.contains("thesportsdb.com")
                || selectors.toLowerCase(Locale.ROOT).contains("thesportsdb"));
    }

    private String leagueIdFromSourceTarget(SourceTarget sourceTarget) {
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

    private boolean applyLeagueArtwork(League league, TheSportsDbLeagueDto sourceLeague, boolean dryRun) {
        boolean changed = false;
        changed |= setIfPresent(league::getBadgeUrl, league::setBadgeUrl, sourceLeague.strBadge(), dryRun);
        changed |= setIfPresent(league::getLogoUrl, league::setLogoUrl, sourceLeague.strLogo(), dryRun);
        changed |= setIfPresent(league::getBannerUrl, league::setBannerUrl, sourceLeague.strBanner(), dryRun);
        changed |= setIfPresent(league::getPosterUrl, league::setPosterUrl, sourceLeague.strPoster(), dryRun);
        changed |= setIfPresent(league::getTrophyUrl, league::setTrophyUrl, sourceLeague.strTrophy(), dryRun);
        changed |= setIfPresent(league::getFanartUrl, league::setFanartUrl, sourceLeague.strFanart1(), dryRun);
        return changed;
    }

    private boolean applyTeamArtwork(Team team, TheSportsDbTeamDto sourceTeam, boolean dryRun) {
        boolean changed = false;
        changed |= setIfPresent(team::getBadgeUrl, team::setBadgeUrl, sourceTeam.strBadge(), dryRun);
        changed |= setIfPresent(team::getLogoUrl, team::setLogoUrl, sourceTeam.strLogo(), dryRun);
        changed |= setIfPresent(team::getBannerUrl, team::setBannerUrl, sourceTeam.strBanner(), dryRun);
        changed |= setIfPresent(team::getEquipmentUrl, team::setEquipmentUrl, sourceTeam.strEquipment(), dryRun);
        changed |= setIfPresent(team::getFanartUrl, team::setFanartUrl, sourceTeam.strFanart1(), dryRun);
        return changed;
    }

    private boolean setIfPresent(Supplier<String> getter, Consumer<String> setter, String candidate, boolean dryRun) {
        String value = nonBlank(candidate);
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String truncated = truncate(value, 1000);
        if (truncated.equals(getter.get())) {
            return false;
        }
        if (dryRun) {
            return true;
        }
        setter.accept(truncated);
        return true;
    }

    private boolean limitReached(Counters counters, Integer limit, boolean teamsOnly, boolean leaguesOnly) {
        if (limit == null || limit <= 0) {
            return false;
        }
        if (teamsOnly) {
            return counters.checkedTeams >= limit;
        }
        if (leaguesOnly) {
            return counters.checkedLeagues >= limit;
        }
        return counters.checkedLeagues + counters.checkedTeams >= limit;
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

    private String nonBlank(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String safeMessage(RuntimeException exception) {
        return StringUtils.hasText(exception.getMessage()) ? exception.getMessage() : exception.getClass().getSimpleName();
    }

    private static final class Counters {
        private int checkedLeagues;
        private int updatedLeagues;
        private int unchangedLeagues;
        private int skippedLeagues;
        private int failedLeagues;
        private int checkedTeams;
        private int updatedTeams;
        private int unchangedTeams;
        private int skippedTeams;
        private int failedTeams;
        private final List<String> skipReasons = new ArrayList<>();
        private final List<String> failureReasons = new ArrayList<>();

        private TheSportsDbArtworkBackfillResponse toResponse(OffsetDateTime startedAt, OffsetDateTime finishedAt) {
            return new TheSportsDbArtworkBackfillResponse(
                    startedAt,
                    finishedAt,
                    checkedLeagues,
                    updatedLeagues,
                    unchangedLeagues,
                    skippedLeagues,
                    failedLeagues,
                    checkedTeams,
                    updatedTeams,
                    unchangedTeams,
                    skippedTeams,
                    failedTeams,
                    List.copyOf(skipReasons),
                    List.copyOf(failureReasons)
            );
        }
    }
}
