package com.betai.service;

import com.betai.api.dto.ModelQualityGenerationRequest;
import com.betai.api.dto.ModelQualityGenerationResponse;
import com.betai.api.dto.ModelQualitySnapshotResponse;
import com.betai.config.PredictionProperties;
import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.market.MarketCode;
import com.betai.domain.market.MarketDefinition;
import com.betai.domain.prediction.PredictionConfidenceBand;
import com.betai.domain.prediction.PredictionOutcome;
import com.betai.domain.prediction.PredictionSelection;
import com.betai.domain.quality.ModelQualitySnapshot;
import com.betai.exception.InvalidRequestException;
import com.betai.exception.ReferenceDataNotFoundException;
import com.betai.repository.LeagueRepository;
import com.betai.repository.ModelQualitySnapshotRepository;
import com.betai.repository.PredictionSelectionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ModelQualityServiceImpl implements ModelQualityService {

    private static final int DEFAULT_MINIMUM_SAMPLE_SIZE = 30;
    private static final MathContext MATH_CONTEXT = new MathContext(12, RoundingMode.HALF_UP);
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
    private static final BigDecimal ONE = BigDecimal.ONE.setScale(6, RoundingMode.HALF_UP);
    private static final BigDecimal MAX_ADJUSTMENT = new BigDecimal("0.150000");

    private final PredictionProperties predictionProperties;
    private final LeagueRepository leagueRepository;
    private final PredictionSelectionRepository predictionSelectionRepository;
    private final ModelQualitySnapshotRepository modelQualitySnapshotRepository;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public ModelQualityGenerationResponse generateQualitySnapshots(ModelQualityGenerationRequest request) {
        OffsetDateTime triggeredAt = OffsetDateTime.now(clock);
        LocalDate qualityDate = request.qualityDate() == null ? LocalDate.now(clock) : request.qualityDate();
        String modelVersion = resolveModelVersion(request.modelVersion());
        int minimumSampleSize = request.minimumSampleSize() == null
                ? DEFAULT_MINIMUM_SAMPLE_SIZE
                : request.minimumSampleSize();
        List<League> leagues = resolveLeagues(request.leagueCodes());
        List<ModelQualitySnapshotResponse> responses = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        FlushModeType previousFlushMode = entityManager.getFlushMode();
        entityManager.setFlushMode(FlushModeType.COMMIT);
        try {
            for (League league : leagues) {
                List<PredictionSelection> selections = predictionSelectionRepository.findSettledSelectionsForQuality(
                        league.getCode(),
                        qualityDate,
                        modelVersion
                );
                if (selections.isEmpty()) {
                    warnings.add("No settled predictions exist for " + league.getCode() + " on or before "
                            + qualityDate + " using model " + modelVersion + ".");
                    continue;
                }

                Map<MarketCode, QualityAccumulator> byMarket = new EnumMap<>(MarketCode.class);
                for (PredictionSelection selection : selections) {
                    MarketDefinition market = selection.getMarketDefinition();
                    byMarket.computeIfAbsent(
                            market.getCode(),
                            ignored -> new QualityAccumulator(league, market, modelVersion, qualityDate)
                    ).add(selection);
                }

                for (QualityAccumulator accumulator : byMarket.values()) {
                    ModelQualitySnapshot snapshot = modelQualitySnapshotRepository
                            .findByLeague_CodeAndMarketDefinition_CodeAndModelVersionAndQualityDate(
                                    league.getCode(),
                                    accumulator.marketDefinition.getCode(),
                                    modelVersion,
                                    qualityDate
                            )
                            .orElseGet(ModelQualitySnapshot::new);
                    responses.add(ModelQualitySnapshotResponse.from(
                            modelQualitySnapshotRepository.save(accumulator.applyTo(snapshot, minimumSampleSize, triggeredAt))
                    ));
                }
            }

            return new ModelQualityGenerationResponse(UUID.randomUUID(), triggeredAt, List.copyOf(responses), List.copyOf(warnings));
        } finally {
            entityManager.setFlushMode(previousFlushMode);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ModelQualitySnapshotResponse> getQualitySnapshots(LeagueCode leagueCode, String modelVersion, LocalDate qualityDate) {
        String resolvedModelVersion = resolveModelVersion(modelVersion);
        LocalDate resolvedQualityDate = qualityDate == null ? LocalDate.now(clock) : qualityDate;
        return modelQualitySnapshotRepository
                .findByLeague_CodeAndModelVersionAndQualityDateOrderByMarketDefinition_CodeAsc(
                        leagueCode,
                        resolvedModelVersion,
                        resolvedQualityDate
                )
                .stream()
                .map(ModelQualitySnapshotResponse::from)
                .toList();
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
        EnumSet<LeagueCode> missing = EnumSet.copyOf(requestedCodes);
        missing.removeAll(activeCodes);
        if (!missing.isEmpty()) {
            throw new ReferenceDataNotFoundException("Unsupported or inactive leagues: " + missing + ".");
        }
        return leagues;
    }

    private static BigDecimal probability(PredictionSelection selection) {
        return selection.getRawProbability() == null ? selection.getProbability() : selection.getRawProbability();
    }

    private static BigDecimal rate(int numerator, int denominator) {
        if (denominator == 0) {
            return ZERO;
        }
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 6, RoundingMode.HALF_UP);
    }

    private static BigDecimal average(BigDecimal numerator, int denominator) {
        if (denominator == 0) {
            return ZERO;
        }
        return numerator.divide(BigDecimal.valueOf(denominator), 6, RoundingMode.HALF_UP);
    }

    private static BigDecimal clampedAdjustment(BigDecimal value) {
        if (value.compareTo(MAX_ADJUSTMENT) > 0) {
            return MAX_ADJUSTMENT;
        }
        BigDecimal min = MAX_ADJUSTMENT.negate();
        if (value.compareTo(min) < 0) {
            return min;
        }
        return value.setScale(6, RoundingMode.HALF_UP);
    }

    private static PredictionConfidenceBand qualityBand(int sampleSize, BigDecimal calibrationError, BigDecimal brierScore, int minimumSampleSize) {
        if (sampleSize < minimumSampleSize) {
            return PredictionConfidenceBand.UNRATED;
        }
        if (sampleSize >= 300
                && calibrationError.compareTo(new BigDecimal("0.020000")) <= 0
                && brierScore.compareTo(new BigDecimal("0.160000")) <= 0) {
            return PredictionConfidenceBand.VERY_HIGH;
        }
        if (sampleSize >= 100
                && calibrationError.compareTo(new BigDecimal("0.035000")) <= 0
                && brierScore.compareTo(new BigDecimal("0.190000")) <= 0) {
            return PredictionConfidenceBand.HIGH;
        }
        if (sampleSize >= 50
                && calibrationError.compareTo(new BigDecimal("0.070000")) <= 0
                && brierScore.compareTo(new BigDecimal("0.240000")) <= 0) {
            return PredictionConfidenceBand.MEDIUM;
        }
        return PredictionConfidenceBand.LOW;
    }

    private static final class QualityAccumulator {
        private final League league;
        private final MarketDefinition marketDefinition;
        private final String modelVersion;
        private final LocalDate qualityDate;
        private int won;
        private int lost;
        private int voided;
        private BigDecimal probabilitySum = BigDecimal.ZERO;
        private BigDecimal brierSum = BigDecimal.ZERO;

        private QualityAccumulator(League league, MarketDefinition marketDefinition, String modelVersion, LocalDate qualityDate) {
            this.league = league;
            this.marketDefinition = marketDefinition;
            this.modelVersion = modelVersion;
            this.qualityDate = qualityDate;
        }

        private void add(PredictionSelection selection) {
            if (selection.getOutcome() == PredictionOutcome.VOID) {
                voided++;
                return;
            }

            BigDecimal actual = selection.getOutcome() == PredictionOutcome.WON ? ONE : ZERO;
            BigDecimal probability = probability(selection);
            BigDecimal error = probability.subtract(actual, MATH_CONTEXT);
            probabilitySum = probabilitySum.add(probability, MATH_CONTEXT);
            brierSum = brierSum.add(error.multiply(error, MATH_CONTEXT), MATH_CONTEXT);

            if (selection.getOutcome() == PredictionOutcome.WON) {
                won++;
            } else if (selection.getOutcome() == PredictionOutcome.LOST) {
                lost++;
            }
        }

        private ModelQualitySnapshot applyTo(ModelQualitySnapshot snapshot, int minimumSampleSize, OffsetDateTime generatedAt) {
            int sampleSize = won + lost;
            BigDecimal observedWinRate = rate(won, sampleSize);
            BigDecimal averageProbability = average(probabilitySum, sampleSize);
            BigDecimal brierScore = average(brierSum, sampleSize);
            BigDecimal calibrationError = averageProbability.subtract(observedWinRate).abs().setScale(6, RoundingMode.HALF_UP);
            PredictionConfidenceBand band = qualityBand(sampleSize, calibrationError, brierScore, minimumSampleSize);
            BigDecimal adjustment = band == PredictionConfidenceBand.UNRATED
                    ? ZERO
                    : clampedAdjustment(observedWinRate.subtract(averageProbability));

            return snapshot.setLeague(league)
                    .setMarketDefinition(marketDefinition)
                    .setModelVersion(modelVersion)
                    .setQualityDate(qualityDate)
                    .setSampleSize(sampleSize)
                    .setWonCount(won)
                    .setLostCount(lost)
                    .setVoidCount(voided)
                    .setObservedWinRate(observedWinRate)
                    .setAverageRawProbability(averageProbability)
                    .setBrierScore(brierScore)
                    .setCalibrationError(calibrationError)
                    .setProbabilityAdjustment(adjustment)
                    .setConfidenceBand(band)
                    .setGeneratedAt(generatedAt);
        }
    }
}
