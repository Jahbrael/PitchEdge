package com.betai.service;

import com.betai.domain.league.LeagueCode;
import com.betai.domain.market.MarketCode;
import com.betai.domain.prediction.PredictionConfidenceBand;
import com.betai.domain.quality.ModelQualitySnapshot;
import com.betai.repository.ModelQualitySnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class ProbabilityCalibrationServiceImpl implements ProbabilityCalibrationService {

    private static final BigDecimal MIN_PROBABILITY = new BigDecimal("0.020000");
    private static final BigDecimal MAX_PROBABILITY = new BigDecimal("0.980000");
    private static final BigDecimal FULL_WEIGHT_SAMPLE_SIZE = new BigDecimal("120");

    private final ModelQualitySnapshotRepository modelQualitySnapshotRepository;

    @Override
    public ProbabilityCalibrationResult calibrate(
            LeagueCode leagueCode,
            MarketCode marketCode,
            String modelVersion,
            LocalDate qualityDate,
            BigDecimal rawProbability
    ) {
        ModelQualitySnapshot snapshot = modelQualitySnapshotRepository
                .findFirstByLeague_CodeAndMarketDefinition_CodeAndModelVersionAndQualityDateLessThanEqualOrderByQualityDateDesc(
                        leagueCode,
                        marketCode,
                        modelVersion,
                        qualityDate
                )
                .orElse(null);

        if (snapshot == null) {
            return new ProbabilityCalibrationResult(
                    scale(rawProbability),
                    scale(rawProbability),
                    PredictionConfidenceBand.UNRATED,
                    null,
                    "No model quality snapshot exists for this league/market/model on or before " + qualityDate + "."
            );
        }

        if (snapshot.getConfidenceBand() == PredictionConfidenceBand.UNRATED || snapshot.getSampleSize() == 0) {
            return new ProbabilityCalibrationResult(
                    scale(rawProbability),
                    scale(rawProbability),
                    PredictionConfidenceBand.UNRATED,
                    snapshot,
                    "Model quality sample is below the configured minimum; raw probability was not adjusted."
            );
        }

        BigDecimal sampleWeight = BigDecimal.valueOf(snapshot.getSampleSize())
                .divide(FULL_WEIGHT_SAMPLE_SIZE, 6, RoundingMode.HALF_UP)
                .min(BigDecimal.ONE);
        BigDecimal calibrated = rawProbability.add(snapshot.getProbabilityAdjustment().multiply(sampleWeight));
        BigDecimal clamped = clamp(calibrated);
        PredictionConfidenceBand selectionBand = selectionConfidenceBand(snapshot.getConfidenceBand(), clamped);

        return new ProbabilityCalibrationResult(
                scale(rawProbability),
                clamped,
                selectionBand,
                snapshot,
                "Calibrated using " + snapshot.getSampleSize() + " settled selections for "
                        + leagueCode + "/" + marketCode + " through " + snapshot.getQualityDate() + "."
        );
    }

    private PredictionConfidenceBand selectionConfidenceBand(PredictionConfidenceBand qualityBand, BigDecimal calibratedProbability) {
        if (qualityBand == PredictionConfidenceBand.LOW) {
            return PredictionConfidenceBand.LOW;
        }
        if (calibratedProbability.compareTo(new BigDecimal("0.780000")) >= 0
                && (qualityBand == PredictionConfidenceBand.HIGH || qualityBand == PredictionConfidenceBand.VERY_HIGH)) {
            return PredictionConfidenceBand.VERY_HIGH;
        }
        if (calibratedProbability.compareTo(new BigDecimal("0.700000")) >= 0) {
            return PredictionConfidenceBand.HIGH;
        }
        if (calibratedProbability.compareTo(new BigDecimal("0.600000")) >= 0) {
            return PredictionConfidenceBand.MEDIUM;
        }
        return PredictionConfidenceBand.LOW;
    }

    private BigDecimal clamp(BigDecimal value) {
        if (value.compareTo(MIN_PROBABILITY) < 0) {
            return MIN_PROBABILITY;
        }
        if (value.compareTo(MAX_PROBABILITY) > 0) {
            return MAX_PROBABILITY;
        }
        return scale(value);
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(6, RoundingMode.HALF_UP);
    }
}
