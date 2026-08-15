package com.betai.service;

import com.betai.api.dto.LeagueMarketReadinessResponse;
import com.betai.api.dto.ModelReadinessStatus;
import com.betai.config.PredictionProperties;
import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.market.MarketCode;
import com.betai.domain.market.MarketDefinition;
import com.betai.domain.prediction.PredictionConfidenceBand;
import com.betai.domain.quality.ModelQualitySnapshot;
import com.betai.domain.source.SourceTarget;
import com.betai.domain.source.SourceType;
import com.betai.domain.tuning.ModelTuningProfile;
import com.betai.exception.InvalidRequestException;
import com.betai.exception.ReferenceDataNotFoundException;
import com.betai.repository.LeagueRepository;
import com.betai.repository.MarketDefinitionRepository;
import com.betai.repository.ModelQualitySnapshotRepository;
import com.betai.repository.ModelTuningProfileRepository;
import com.betai.repository.PredictionSelectionRepository;
import com.betai.repository.SourceTargetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ModelReadinessServiceImpl implements ModelReadinessService {

    private static final int DEFAULT_CALIBRATION_MINIMUM_SAMPLE_SIZE = 30;

    private final PredictionProperties predictionProperties;
    private final LeagueRepository leagueRepository;
    private final MarketDefinitionRepository marketDefinitionRepository;
    private final SourceTargetRepository sourceTargetRepository;
    private final PredictionSelectionRepository predictionSelectionRepository;
    private final ModelQualitySnapshotRepository modelQualitySnapshotRepository;
    private final ModelTuningProfileRepository modelTuningProfileRepository;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<LeagueMarketReadinessResponse> getReadiness(
            Set<LeagueCode> leagueCodes,
            Set<MarketCode> marketCodes,
            String modelVersion,
            LocalDate asOfDate
    ) {
        String resolvedModelVersion = resolveModelVersion(modelVersion);
        LocalDate resolvedAsOfDate = asOfDate == null ? LocalDate.now(clock) : asOfDate;
        List<League> leagues = resolveLeagues(leagueCodes);
        List<MarketDefinition> markets = resolveMarkets(marketCodes);
        Map<LeagueCode, EnumSet<SourceType>> activeSourceTypes = activeSourceTypes(leagues);
        List<LeagueMarketReadinessResponse> responses = new ArrayList<>();

        for (League league : leagues) {
            for (MarketDefinition market : markets) {
                responses.add(readinessFor(league, market, resolvedModelVersion, resolvedAsOfDate, activeSourceTypes));
            }
        }

        return responses.stream()
                .sorted(Comparator
                        .comparing(LeagueMarketReadinessResponse::leagueCode)
                        .thenComparing(LeagueMarketReadinessResponse::marketCode))
                .toList();
    }

    private LeagueMarketReadinessResponse readinessFor(
            League league,
            MarketDefinition market,
            String modelVersion,
            LocalDate asOfDate,
            Map<LeagueCode, EnumSet<SourceType>> activeSourceTypes
    ) {
        EnumSet<SourceType> sourceTypes = activeSourceTypes.getOrDefault(
                league.getCode(),
                EnumSet.noneOf(SourceType.class)
        );
        boolean hasMatchDataSource = sourceTypes.contains(SourceType.MATCH_DATA);
        boolean hasResultsSource = hasMatchDataSource || sourceTypes.contains(SourceType.RESULTS);
        boolean hasFixtureSource = hasMatchDataSource || sourceTypes.contains(SourceType.FIXTURES);
        boolean hasOddsReferenceSource = sourceTypes.contains(SourceType.ODDS_REFERENCE);
        int minimumSampleSizeRequired = Math.max(
                DEFAULT_CALIBRATION_MINIMUM_SAMPLE_SIZE,
                market.getMinimumSampleSize()
        );
        long settledSelectionsFound = predictionSelectionRepository.countResolvedSelectionsForReadiness(
                league.getCode(),
                market.getCode(),
                modelVersion
        );
        long pricedSelectionsFound = predictionSelectionRepository.countPricedSelectionsForReadiness(
                league.getCode(),
                market.getCode(),
                modelVersion
        );
        ModelQualitySnapshot quality = modelQualitySnapshotRepository
                .findFirstByLeague_CodeAndMarketDefinition_CodeAndModelVersionAndQualityDateLessThanEqualOrderByQualityDateDesc(
                        league.getCode(),
                        market.getCode(),
                        modelVersion,
                        asOfDate
                )
                .orElse(null);
        ModelTuningProfile tuningProfile = modelTuningProfileRepository
                .findFirstByLeague_CodeAndMarketDefinition_CodeAndModelVersionAndSegmentKeyAndProfileDateLessThanEqualAndActiveTrueOrderByProfileDateDesc(
                        league.getCode(),
                        market.getCode(),
                        modelVersion,
                        TuningSegment.GLOBAL,
                        asOfDate
                )
                .orElse(null);

        boolean hasQualitySnapshot = quality != null;
        boolean calibrationReady = hasQualitySnapshot
                && quality.getSampleSize() >= minimumSampleSizeRequired
                && quality.getConfidenceBand() != PredictionConfidenceBand.UNRATED;
        boolean hasActiveGlobalTuningProfile = tuningProfile != null;
        boolean optimizedProbabilityReady = hasResultsSource
                && hasFixtureSource
                && calibrationReady
                && hasActiveGlobalTuningProfile;
        boolean valueStrategyDataReady = optimizedProbabilityReady
                && hasOddsReferenceSource
                && pricedSelectionsFound > 0;
        List<String> missingSteps = missingSteps(
                league,
                market,
                minimumSampleSizeRequired,
                settledSelectionsFound,
                hasResultsSource,
                hasFixtureSource,
                hasOddsReferenceSource,
                hasQualitySnapshot,
                calibrationReady,
                hasActiveGlobalTuningProfile,
                pricedSelectionsFound
        );

        return new LeagueMarketReadinessResponse(
                league.getCode().name(),
                league.getName(),
                market.getCode().name(),
                market.getDisplayName(),
                modelVersion,
                asOfDate,
                minimumSampleSizeRequired,
                settledSelectionsFound,
                pricedSelectionsFound,
                hasResultsSource,
                hasFixtureSource,
                hasOddsReferenceSource,
                hasQualitySnapshot,
                quality == null ? null : quality.getQualityDate(),
                quality == null ? null : quality.getSampleSize(),
                quality == null ? null : quality.getConfidenceBand(),
                quality == null ? null : quality.getBrierScore(),
                quality == null ? null : quality.getCalibrationError(),
                calibrationReady,
                hasActiveGlobalTuningProfile,
                tuningProfile == null ? null : tuningProfile.getProfileDate(),
                tuningProfile == null ? null : tuningProfile.getSampleSize(),
                optimizedProbabilityReady,
                valueStrategyDataReady,
                status(optimizedProbabilityReady, settledSelectionsFound, hasQualitySnapshot, hasActiveGlobalTuningProfile),
                missingSteps
        );
    }

    private List<String> missingSteps(
            League league,
            MarketDefinition market,
            int minimumSampleSizeRequired,
            long settledSelectionsFound,
            boolean hasResultsSource,
            boolean hasFixtureSource,
            boolean hasOddsReferenceSource,
            boolean hasQualitySnapshot,
            boolean calibrationReady,
            boolean hasActiveGlobalTuningProfile,
            long pricedSelectionsFound
    ) {
        List<String> missing = new ArrayList<>();
        String pair = league.getCode() + "/" + market.getCode();
        if (!hasResultsSource) {
            missing.add("Configure and import an active RESULTS source for " + league.getCode() + ".");
        }
        if (!hasFixtureSource) {
            missing.add("Configure and import an active FIXTURES source for " + league.getCode() + ".");
        }
        if (settledSelectionsFound < minimumSampleSizeRequired) {
            missing.add("Generate and settle at least " + minimumSampleSizeRequired
                    + " non-void historical predictions for " + pair + ".");
        }
        if (!hasQualitySnapshot) {
            missing.add("Generate a model quality snapshot for " + pair + ".");
        } else if (!calibrationReady) {
            missing.add("Regenerate model quality for " + pair
                    + " after enough historical sample exists; UNRATED quality is not calibration-ready.");
        }
        if (!hasActiveGlobalTuningProfile) {
            missing.add("Run backtesting for " + pair + " to create an active GLOBAL tuning profile.");
        }
        if (!hasOddsReferenceSource) {
            missing.add("Configure an ODDS_REFERENCE source before using value-based strategies for " + league.getCode() + ".");
        } else if (pricedSelectionsFound == 0) {
            missing.add("Extract/import odds snapshots for " + pair + " before using value-based strategies.");
        }
        return List.copyOf(missing);
    }

    private ModelReadinessStatus status(
            boolean optimizedProbabilityReady,
            long settledSelectionsFound,
            boolean hasQualitySnapshot,
            boolean hasActiveGlobalTuningProfile
    ) {
        if (optimizedProbabilityReady) {
            return ModelReadinessStatus.READY;
        }
        if (settledSelectionsFound > 0 || hasQualitySnapshot || hasActiveGlobalTuningProfile) {
            return ModelReadinessStatus.PARTIAL;
        }
        return ModelReadinessStatus.NOT_READY;
    }

    private Map<LeagueCode, EnumSet<SourceType>> activeSourceTypes(List<League> leagues) {
        Set<LeagueCode> leagueCodes = leagues.stream()
                .map(League::getCode)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(LeagueCode.class)));
        List<SourceTarget> sourceTargets = sourceTargetRepository.findActiveByLeagueCodes(leagueCodes);
        Map<LeagueCode, EnumSet<SourceType>> byLeague = new EnumMap<>(LeagueCode.class);
        for (SourceTarget sourceTarget : sourceTargets) {
            byLeague.computeIfAbsent(
                    sourceTarget.getLeague().getCode(),
                    ignored -> EnumSet.noneOf(SourceType.class)
            ).add(sourceTarget.getSourceType());
        }
        return byLeague;
    }

    private String resolveModelVersion(String requestedModelVersion) {
        String modelVersion = StringUtils.hasText(requestedModelVersion)
                ? requestedModelVersion.trim()
                : predictionProperties.defaultModelVersion();
        if (!StringUtils.hasText(modelVersion)) {
            throw new InvalidRequestException("modelVersion is required when no default model version is configured.");
        }
        if (modelVersion.length() > 80) {
            throw new InvalidRequestException("modelVersion cannot exceed 80 characters.");
        }
        return modelVersion;
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
        EnumSet<LeagueCode> missing = EnumSet.noneOf(LeagueCode.class);
        missing.addAll(requestedCodes);
        missing.removeAll(activeCodes);
        if (!missing.isEmpty()) {
            throw new ReferenceDataNotFoundException("Unsupported or inactive leagues: " + missing + ".");
        }
        return leagues;
    }

    private List<MarketDefinition> resolveMarkets(Set<MarketCode> requestedCodes) {
        if (requestedCodes == null || requestedCodes.isEmpty()) {
            List<MarketDefinition> markets = marketDefinitionRepository.findByEnabledTrueOrderByDisplayNameAsc();
            if (markets.isEmpty()) {
                throw new ReferenceDataNotFoundException("No enabled markets are configured.");
            }
            return markets;
        }

        List<MarketDefinition> markets = marketDefinitionRepository.findByCodeInAndEnabledTrue(requestedCodes);
        Set<MarketCode> activeCodes = markets.stream().map(MarketDefinition::getCode).collect(Collectors.toSet());
        EnumSet<MarketCode> missing = EnumSet.noneOf(MarketCode.class);
        missing.addAll(requestedCodes);
        missing.removeAll(activeCodes);
        if (!missing.isEmpty()) {
            throw new ReferenceDataNotFoundException("Unsupported or disabled markets: " + missing + ".");
        }
        return markets;
    }
}
