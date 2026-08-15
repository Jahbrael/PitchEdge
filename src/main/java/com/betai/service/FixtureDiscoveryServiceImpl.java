package com.betai.service;

import com.betai.api.dto.DailyExtractionRequest;
import com.betai.api.dto.DailyRefreshRequest;
import com.betai.api.dto.FixtureDiscoveryRequest;
import com.betai.api.dto.FixtureDiscoveryResponse;
import com.betai.api.dto.FootballDataFixtureSourceRegistrationRequest;
import com.betai.api.dto.FootballDataFixtureSourceRegistrationResponse;
import com.betai.api.dto.PendingSlateGenerationRequest;
import com.betai.api.dto.PredictionGenerationResponse;
import com.betai.api.dto.UpcomingFixtureResponse;
import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.match.MatchStatus;
import com.betai.exception.InvalidRequestException;
import com.betai.exception.ReferenceDataNotFoundException;
import com.betai.repository.LeagueRepository;
import com.betai.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FixtureDiscoveryServiceImpl implements FixtureDiscoveryService {

    private static final int DEFAULT_FIXTURE_WINDOW_DAYS = 30;
    private static final int MAX_FIXTURE_WINDOW_DAYS = 370;
    private static final int MINIMUM_FIXTURES_PER_LEAGUE_WARNING = 3;

    private final LeagueRepository leagueRepository;
    private final MatchRepository matchRepository;
    private final FootballDataFixtureSourceService footballDataFixtureSourceService;
    private final DailyRefreshService dailyRefreshService;
    private final ExtractionService extractionService;
    private final PendingSlateGenerationService pendingSlateGenerationService;
    private final Clock clock;

    @Override
    public FixtureDiscoveryResponse discoverFixtures(FixtureDiscoveryRequest request) {
        OffsetDateTime triggeredAt = OffsetDateTime.now(clock);
        LocalDate discoveryDate = request.discoveryDate() == null ? LocalDate.now(clock) : request.discoveryDate();
        LocalDate fixtureDateFrom = request.fixtureDateFrom() == null ? discoveryDate : request.fixtureDateFrom();
        LocalDate fixtureDateTo = request.fixtureDateTo() == null
                ? fixtureDateFrom.plusDays(DEFAULT_FIXTURE_WINDOW_DAYS - 1L)
                : request.fixtureDateTo();
        validateDateRange(fixtureDateFrom, fixtureDateTo);

        Set<LeagueCode> leagueCodes = resolveLeagueCodes(request.leagueCodes());
        boolean autoRegisterFootballDataSources = request.autoRegisterFootballDataSources() == null
                || request.autoRegisterFootballDataSources();
        String targetSeasonLabel = resolveTargetSeasonLabel(request.targetSeasonLabel(), discoveryDate, autoRegisterFootballDataSources);
        List<String> warnings = new ArrayList<>();

        FootballDataFixtureSourceRegistrationResponse sourceRegistration = null;
        if (autoRegisterFootballDataSources) {
            sourceRegistration = footballDataFixtureSourceService.registerLatestFixtureSources(
                    new FootballDataFixtureSourceRegistrationRequest(
                            targetSeasonLabel,
                            true,
                            true,
                            6,
                            10000
                    )
            );
        }

        var refresh = dailyRefreshService.triggerDailyRefresh(new DailyRefreshRequest(
                leagueCodes,
                discoveryDate,
                Boolean.TRUE.equals(request.forceRefresh())
        ));
        var extraction = extractionService.extractDailySnapshots(new DailyExtractionRequest(
                leagueCodes,
                discoveryDate,
                Boolean.TRUE.equals(request.forceReprocess())
        ));

        List<UpcomingFixtureResponse> discoveredFixtures = matchRepository.findCandidateFixtures(
                        leagueCodes,
                        fixtureDateFrom,
                        fixtureDateTo,
                        List.of(MatchStatus.SCHEDULED)
                )
                .stream()
                .map(UpcomingFixtureResponse::from)
                .toList();

        if (discoveredFixtures.isEmpty()) {
            warnings.add("No scheduled fixtures were found for the requested leagues/date window after refresh and extraction. The configured source may not have published future fixtures yet.");
        }
        addLowFixtureCoverageWarnings(leagueCodes, discoveredFixtures, fixtureDateFrom, fixtureDateTo, warnings);

        PredictionGenerationResponse pendingSlateGeneration = null;
        if (Boolean.TRUE.equals(request.generatePendingSlate())) {
            if (discoveredFixtures.isEmpty()) {
                warnings.add("Pending slate generation was skipped because no scheduled fixtures were discovered.");
            } else {
                pendingSlateGeneration = pendingSlateGenerationService.generatePendingSlate(new PendingSlateGenerationRequest(
                        leagueCodes,
                        fixtureDateFrom,
                        fixtureDateTo,
                        request.modelVersion(),
                        request.forceRegeneratePredictions()
                ));
            }
        }

        return new FixtureDiscoveryResponse(
                UUID.randomUUID(),
                triggeredAt,
                discoveryDate,
                fixtureDateFrom,
                fixtureDateTo,
                targetSeasonLabel,
                sourceRegistration,
                refresh,
                extraction,
                List.copyOf(discoveredFixtures),
                pendingSlateGeneration,
                List.copyOf(warnings)
        );
    }

    private void addLowFixtureCoverageWarnings(
            Set<LeagueCode> leagueCodes,
            List<UpcomingFixtureResponse> discoveredFixtures,
            LocalDate fixtureDateFrom,
            LocalDate fixtureDateTo,
            List<String> warnings
    ) {
        Map<String, Long> fixturesByLeague = discoveredFixtures.stream()
                .collect(Collectors.groupingBy(
                        UpcomingFixtureResponse::leagueCode,
                        Collectors.counting()
                ));

        leagueCodes.stream()
                .map(Enum::name)
                .sorted()
                .forEach(leagueCode -> {
                    long fixturesFound = fixturesByLeague.getOrDefault(leagueCode, 0L);
                    if (fixturesFound < MINIMUM_FIXTURES_PER_LEAGUE_WARNING) {
                        warnings.add("Only " + fixturesFound + " scheduled fixture(s) were discovered for "
                                + leagueCode + " between " + fixtureDateFrom + " and " + fixtureDateTo
                                + ". Verify the primary fixture source and fallback coverage.");
                    }
                });
    }

    private Set<LeagueCode> resolveLeagueCodes(Set<LeagueCode> requestedCodes) {
        if (requestedCodes == null || requestedCodes.isEmpty()) {
            List<League> leagues = leagueRepository.findByActiveTrueAndScrapeEnabledTrueOrderByNameAsc();
            if (leagues.isEmpty()) {
                throw new ReferenceDataNotFoundException("No active scrape-enabled leagues are configured.");
            }
            return leagues.stream()
                    .map(League::getCode)
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(LeagueCode.class)));
        }

        List<League> leagues = leagueRepository.findByCodeInAndActiveTrue(requestedCodes);
        Set<LeagueCode> activeCodes = leagues.stream().map(League::getCode).collect(Collectors.toSet());
        EnumSet<LeagueCode> missing = EnumSet.copyOf(requestedCodes);
        missing.removeAll(activeCodes);
        if (!missing.isEmpty()) {
            throw new ReferenceDataNotFoundException("Unsupported or inactive leagues: " + missing + ".");
        }
        return EnumSet.copyOf(requestedCodes);
    }

    private String resolveTargetSeasonLabel(String requestedTargetSeasonLabel, LocalDate discoveryDate, boolean autoRegister) {
        if (StringUtils.hasText(requestedTargetSeasonLabel)) {
            return requestedTargetSeasonLabel.trim();
        }
        if (!autoRegister) {
            return null;
        }
        return defaultTargetSeasonLabel(discoveryDate);
    }

    private String defaultTargetSeasonLabel(LocalDate date) {
        int startYear = date.getMonthValue() >= 6 ? date.getYear() : date.getYear() - 1;
        return startYear + "/" + (startYear + 1);
    }

    private void validateDateRange(LocalDate fixtureDateFrom, LocalDate fixtureDateTo) {
        if (fixtureDateTo.isBefore(fixtureDateFrom)) {
            throw new InvalidRequestException("fixtureDateTo cannot be before fixtureDateFrom.");
        }
        long days = fixtureDateTo.toEpochDay() - fixtureDateFrom.toEpochDay() + 1;
        if (days > MAX_FIXTURE_WINDOW_DAYS) {
            throw new InvalidRequestException("Fixture discovery date range cannot exceed " + MAX_FIXTURE_WINDOW_DAYS + " days.");
        }
    }
}
