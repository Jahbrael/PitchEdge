package com.betai.service;

import com.betai.api.dto.PendingSlateGenerationRequest;
import com.betai.api.dto.PredictionGenerationRequest;
import com.betai.api.dto.PredictionGenerationResponse;
import com.betai.api.dto.PredictionGenerationRunResponse;
import com.betai.domain.feature.FeatureGenerationRun;
import com.betai.domain.feature.FeatureGenerationStatus;
import com.betai.domain.feature.SeasonSelectionMode;
import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.match.MatchStatus;
import com.betai.exception.ReferenceDataNotFoundException;
import com.betai.repository.FeatureGenerationRunRepository;
import com.betai.repository.LeagueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PendingSlateGenerationServiceImpl implements PendingSlateGenerationService {

    private static final int DEFAULT_WINDOW_DAYS = 14;

    private final LeagueRepository leagueRepository;
    private final FeatureGenerationRunRepository featureGenerationRunRepository;
    private final PredictionGenerationService predictionGenerationService;
    private final Clock clock;

    @Override
    @Transactional
    public PredictionGenerationResponse generatePendingSlate(PendingSlateGenerationRequest request) {
        OffsetDateTime triggeredAt = OffsetDateTime.now(clock);
        LocalDate fixtureDateFrom = request.fixtureDateFrom() == null ? LocalDate.now(clock) : request.fixtureDateFrom();
        LocalDate fixtureDateTo = request.fixtureDateTo() == null ? fixtureDateFrom.plusDays(DEFAULT_WINDOW_DAYS - 1L) : request.fixtureDateTo();
        boolean forceRegenerate = Boolean.TRUE.equals(request.forceRegenerate());
        List<League> leagues = resolveLeagues(request.leagueCodes());
        List<PredictionGenerationRunResponse> runs = new ArrayList<>();

        for (League league : leagues) {
            FeatureGenerationRun featureRun = latestSuccessfulFeatureRun(league.getCode());
            PredictionGenerationResponse response = predictionGenerationService.generatePredictions(new PredictionGenerationRequest(
                    Set.of(league.getCode()),
                    featureRun.getCalculationDate(),
                    featureRun.getSeasonLabel(),
                    fixtureDateFrom,
                    fixtureDateTo,
                    EnumSet.of(MatchStatus.SCHEDULED),
                    request.modelVersion(),
                    null,
                    forceRegenerate,
                    featureRun.getRequestedSeasonCount(),
                    seasonSelectionMode(featureRun),
                    null
            ));
            runs.addAll(response.predictionGenerationRuns());
        }

        return new PredictionGenerationResponse(UUID.randomUUID(), triggeredAt, List.copyOf(runs));
    }

    private SeasonSelectionMode seasonSelectionMode(FeatureGenerationRun featureRun) {
        if (featureRun.getSeasonSelectionMode() == null || featureRun.getSeasonSelectionMode().isBlank()) {
            return null;
        }
        return SeasonSelectionMode.valueOf(featureRun.getSeasonSelectionMode());
    }

    private FeatureGenerationRun latestSuccessfulFeatureRun(LeagueCode leagueCode) {
        return featureGenerationRunRepository
                .findFirstByLeague_CodeAndFeatureStatusOrderByCalculationDateDescStartedAtDesc(
                        leagueCode,
                        FeatureGenerationStatus.SUCCESS
                )
                .orElseThrow(() -> new ReferenceDataNotFoundException("No successful feature generation run exists for "
                        + leagueCode + ". Run feature generation before creating a pending slate."));
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
}
