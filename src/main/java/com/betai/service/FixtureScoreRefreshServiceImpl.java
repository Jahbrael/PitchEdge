package com.betai.service;

import com.betai.api.dto.FixtureScoreRefreshSummary;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.match.Match;
import com.betai.domain.match.MatchStatus;
import com.betai.domain.source.ExternalEntityType;
import com.betai.domain.source.ExternalSourceMapping;
import com.betai.domain.source.ExternalSourceType;
import com.betai.integration.thesportsdb.client.TheSportsDbClient;
import com.betai.integration.thesportsdb.client.TheSportsDbClientResponse;
import com.betai.integration.thesportsdb.dto.TheSportsDbEventStatisticsImportSummary;
import com.betai.integration.thesportsdb.dto.TheSportsDbEventDto;
import com.betai.integration.thesportsdb.mapper.TheSportsDbMapper;
import com.betai.integration.thesportsdb.service.TheSportsDbEventEnrichmentService;
import com.betai.integration.thesportsdb.TheSportsDbProperties;
import com.betai.repository.ExternalSourceMappingRepository;
import com.betai.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class FixtureScoreRefreshServiceImpl implements FixtureScoreRefreshService {

    private final MatchRepository matchRepository;
    private final ExternalSourceMappingRepository externalSourceMappingRepository;
    private final TheSportsDbClient theSportsDbClient;
    private final TheSportsDbMapper theSportsDbMapper;
    private final TheSportsDbEventEnrichmentService eventEnrichmentService;
    private final TheSportsDbProperties theSportsDbProperties;
    private final Clock clock;

    private final ConcurrentHashMap<LocalDate, Boolean> runningRefreshes = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public FixtureScoreRefreshSummary refreshScores(LocalDate date, Set<LeagueCode> leagues) {
        return refreshScores(date, leagues, List.of(MatchStatus.values()));
    }

    @Override
    @Transactional
    public FixtureScoreRefreshSummary refreshLiveScores(LocalDate date) {
        return refreshScores(date, null, List.of(MatchStatus.SCHEDULED, MatchStatus.LIVE, MatchStatus.FINISHED));
    }

    private FixtureScoreRefreshSummary refreshScores(LocalDate date, Set<LeagueCode> leagues, List<MatchStatus> statuses) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (runningRefreshes.putIfAbsent(date, Boolean.TRUE) != null) {
            log.warn("Score refresh already running for date {}", date);
            return new FixtureScoreRefreshSummary(
                    date, 0, 0, 0, 0, 0, 0, 0, now, "Score refresh already running"
            );
        }

        try {
            List<Match> candidateMatches;
            if (leagues != null && !leagues.isEmpty()) {
                candidateMatches = matchRepository.findCandidateFixtures(leagues, date, date, statuses);
            } else {
                candidateMatches = matchRepository.findCandidateFixtures(Set.of(LeagueCode.values()), date, date, statuses);
            }

            if (candidateMatches.isEmpty()) {
                return new FixtureScoreRefreshSummary(
                        date, 0, 0, 0, 0, 0, 0, 0, now, null
                );
            }

            Map<String, TheSportsDbEventDto> liveEventsById = new HashMap<>();
            Map<String, TheSportsDbEventDto> liveEventsByTeamKey = new HashMap<>();
            try {
                TheSportsDbClientResponse liveResp = theSportsDbClient.liveScoreSoccer();
                List<TheSportsDbEventDto> liveList = theSportsDbMapper.events(liveResp.rawJson());
                for (TheSportsDbEventDto ev : liveList) {
                    if (StringUtils.hasText(ev.externalEventId())) {
                        liveEventsById.put(ev.externalEventId().trim(), ev);
                    }
                    String key = teamKey(ev.homeTeamName(), ev.awayTeamName());
                    if (StringUtils.hasText(key)) {
                        liveEventsByTeamKey.put(key, ev);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to fetch liveScoreSoccer during score refresh: {}", e.getMessage());
            }

            boolean apiReturnedEvents = !liveEventsById.isEmpty() || !liveEventsByTeamKey.isEmpty();
            boolean anyLocalMatched = false;

            int checked = 0;
            int updated = 0;
            int unchanged = 0;
            int liveUpdated = 0;
            int finishedUpdated = 0;
            int failed = 0;

            for (Match match : candidateMatches) {
                checked++;
                try {
                    Optional<String> externalIdOpt = findExternalEventId(match);
                    TheSportsDbEventDto eventDto = null;
                    if (externalIdOpt.isPresent()) {
                        eventDto = liveEventsById.get(externalIdOpt.get());
                        if (eventDto == null) {
                            try {
                                TheSportsDbClientResponse evResp = theSportsDbClient.lookupEvent(externalIdOpt.get());
                                List<TheSportsDbEventDto> evList = theSportsDbMapper.events(evResp.rawJson());
                                if (!evList.isEmpty()) {
                                    eventDto = evList.get(0);
                                    apiReturnedEvents = true;
                                }
                            } catch (Exception ex) {
                                log.debug("lookupEvent failed for ID {}: {}", externalIdOpt.get(), ex.getMessage());
                            }
                        }
                    }

                    if (eventDto == null) {
                        String tKey = teamKey(match.getHomeTeam().getCanonicalName(), match.getAwayTeam().getCanonicalName());
                        eventDto = liveEventsByTeamKey.get(tKey);
                    }

                    if (eventDto != null) {
                        anyLocalMatched = true;
                        boolean changed = applyUpdate(match, eventDto, now);
                        refreshLiveStatsIfNeeded(match, externalIdOpt, eventDto);
                        if (changed) {
                            updated++;
                            if (match.getStatus() == MatchStatus.LIVE) {
                                liveUpdated++;
                            } else if (match.getStatus() == MatchStatus.FINISHED) {
                                finishedUpdated++;
                            }
                        } else {
                            match.setScoreRefreshedAt(now);
                            matchRepository.save(match);
                            unchanged++;
                        }
                    } else {
                        log.info("Unmatched event during refresh - External ID: {}, League: {}, Home Team: {}, Away Team: {}, Kickoff: {}, Reason: No matching event returned from TheSportsDB APIs",
                                externalIdOpt.orElse("N/A"),
                                match.getLeague() != null ? match.getLeague().getName() : "Unknown",
                                match.getHomeTeam() != null ? match.getHomeTeam().getCanonicalName() : "Unknown",
                                match.getAwayTeam() != null ? match.getAwayTeam().getCanonicalName() : "Unknown",
                                match.getKickoffAt());
                        match.setScoreRefreshedAt(now);
                        matchRepository.save(match);
                        unchanged++;
                    }
                } catch (Exception e) {
                    log.error("Error refreshing match {}: {}", match.getId(), e.getMessage());
                    failed++;
                }
            }

            String failureReason = null;
            if (updated == 0) {
                if (failed > 0 && checked == failed) {
                    failureReason = "Refresh failed";
                } else if (!apiReturnedEvents) {
                    failureReason = "TheSportsDB returned no events for this date";
                } else if (!anyLocalMatched) {
                    failureReason = "No matching local fixtures found";
                } else {
                    failureReason = "No score changes returned by TheSportsDB";
                }
            }

            return new FixtureScoreRefreshSummary(
                    date, checked, updated, unchanged, liveUpdated, finishedUpdated, failed, 0, now, failureReason
            );
        } finally {
            runningRefreshes.remove(date);
        }
    }

    private boolean applyUpdate(Match match, TheSportsDbEventDto event, OffsetDateTime now) {
        boolean changed = false;

        MatchStatus newStatus = event.status();
        if (newStatus != null && newStatus != match.getStatus()) {
            match.setStatus(newStatus);
            changed = true;
        }

        if (event.homeScore() != null && !Objects.equals(event.homeScore(), match.getHomeScore())) {
            match.setHomeScore(event.homeScore());
            changed = true;
        }
        if (event.awayScore() != null && !Objects.equals(event.awayScore(), match.getAwayScore())) {
            match.setAwayScore(event.awayScore());
            changed = true;
        }
        if (event.homeHalfTimeScore() != null && !Objects.equals(event.homeHalfTimeScore(), match.getHomeHalfTimeScore())) {
            match.setHomeHalfTimeScore(event.homeHalfTimeScore());
            changed = true;
        }
        if (event.awayHalfTimeScore() != null && !Objects.equals(event.awayHalfTimeScore(), match.getAwayHalfTimeScore())) {
            match.setAwayHalfTimeScore(event.awayHalfTimeScore());
            changed = true;
        }

        String newLiveMinute = null;
        if (match.getStatus() == MatchStatus.LIVE && StringUtils.hasText(event.progress())) {
            String prog = event.progress().trim();
            if (!prog.equalsIgnoreCase("live") && !prog.equalsIgnoreCase("in play")) {
                newLiveMinute = prog;
            }
        }
        if (!Objects.equals(newLiveMinute, match.getLiveMinute())) {
            match.setLiveMinute(newLiveMinute);
            changed = true;
        }

        match.setScoreRefreshedAt(now);
        matchRepository.save(match);
        return changed;
    }

    private void refreshLiveStatsIfNeeded(
            Match match,
            Optional<String> externalIdOpt,
            TheSportsDbEventDto eventDto
    ) {
        if (!theSportsDbProperties.liveStatsEnabled() || match.getStatus() != MatchStatus.LIVE) {
            return;
        }
        String externalEventId = externalIdOpt.orElse(null);
        if (!StringUtils.hasText(externalEventId) && eventDto != null && StringUtils.hasText(eventDto.externalEventId())) {
            externalEventId = eventDto.externalEventId().trim();
        }
        if (!StringUtils.hasText(externalEventId)) {
            return;
        }
        try {
            TheSportsDbEventStatisticsImportSummary summary = eventEnrichmentService.importEventStatisticsForMatch(match.getId(), externalEventId);
            if (summary.statisticsImported() == 0 && summary.fixedMatchStatisticsUpdated() == 0) {
                log.info("No live match statistics returned for match {} / TheSportsDB event {} during live refresh.",
                        match.getId(), externalEventId);
            }
        } catch (Exception exception) {
            log.warn("Live stats refresh skipped for match {} / TheSportsDB event {}: {}",
                    match.getId(), externalEventId, exception.getMessage());
        }
    }

    private Optional<String> findExternalEventId(Match match) {
        Optional<ExternalSourceMapping> mapping = externalSourceMappingRepository.findBySourceTypeAndEntityTypeAndInternalEntityId(
                ExternalSourceType.THESPORTSDB,
                ExternalEntityType.EVENT,
                match.getId()
        );
        if (mapping.isPresent() && StringUtils.hasText(mapping.get().getExternalEntityId())) {
            return Optional.of(mapping.get().getExternalEntityId().trim());
        }
        mapping = externalSourceMappingRepository.findBySourceTypeAndEntityTypeAndInternalEntityId(
                ExternalSourceType.THESPORTSDB,
                ExternalEntityType.FIXTURE,
                match.getId()
        );
        if (mapping.isPresent() && StringUtils.hasText(mapping.get().getExternalEntityId())) {
            return Optional.of(mapping.get().getExternalEntityId().trim());
        }
        String key = match.getSourceFixtureKey();
        if (StringUtils.hasText(key)) {
            if (key.startsWith("TSD:")) {
                return Optional.of(key.substring(4).trim());
            }
            if (key.matches("^\\d+$")) {
                return Optional.of(key.trim());
            }
        }
        return Optional.empty();
    }

    private String teamKey(String home, String away) {
        if (!StringUtils.hasText(home) || !StringUtils.hasText(away)) return null;
        return home.trim().toLowerCase() + "::" + away.trim().toLowerCase();
    }
}
