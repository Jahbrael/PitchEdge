package com.betai.service;

import com.betai.domain.league.LeagueCode;
import com.betai.domain.market.MarketCode;
import com.betai.domain.prediction.PredictionConfidenceBand;
import com.betai.domain.quality.ModelQualitySnapshot;
import com.betai.repository.ModelQualitySnapshotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProbabilityCalibrationServiceImplTest {

    @Mock
    private ModelQualitySnapshotRepository modelQualitySnapshotRepository;

    @Test
    void appliesSampleWeightedCalibrationAdjustment() {
        var qualityDate = LocalDate.parse("2026-06-07");
        ModelQualitySnapshot snapshot = new ModelQualitySnapshot()
                .setModelVersion("model-v1")
                .setQualityDate(qualityDate)
                .setSampleSize(60)
                .setProbabilityAdjustment(new BigDecimal("0.080000"))
                .setCalibrationError(new BigDecimal("0.020000"))
                .setConfidenceBand(PredictionConfidenceBand.HIGH);
        snapshot.setId(UUID.randomUUID());

        when(modelQualitySnapshotRepository
                .findFirstByLeague_CodeAndMarketDefinition_CodeAndModelVersionAndQualityDateLessThanEqualOrderByQualityDateDesc(
                        LeagueCode.PREMIER_LEAGUE,
                        MarketCode.HOME_WIN,
                        "model-v1",
                        qualityDate
                )).thenReturn(Optional.of(snapshot));

        var service = new ProbabilityCalibrationServiceImpl(modelQualitySnapshotRepository);

        var result = service.calibrate(
                LeagueCode.PREMIER_LEAGUE,
                MarketCode.HOME_WIN,
                "model-v1",
                qualityDate,
                new BigDecimal("0.700000")
        );

        assertThat(result.rawProbability()).isEqualByComparingTo("0.700000");
        assertThat(result.calibratedProbability()).isEqualByComparingTo("0.740000");
        assertThat(result.confidenceBand()).isEqualTo(PredictionConfidenceBand.HIGH);
        assertThat(result.modelQualitySnapshot()).isSameAs(snapshot);
    }

    @Test
    void keepsRawProbabilityWhenNoQualitySnapshotExists() {
        var qualityDate = LocalDate.parse("2026-06-07");
        when(modelQualitySnapshotRepository
                .findFirstByLeague_CodeAndMarketDefinition_CodeAndModelVersionAndQualityDateLessThanEqualOrderByQualityDateDesc(
                        LeagueCode.ELITESERIEN,
                        MarketCode.OVER_2_5_GOALS,
                        "model-v1",
                        qualityDate
                )).thenReturn(Optional.empty());

        var service = new ProbabilityCalibrationServiceImpl(modelQualitySnapshotRepository);

        var result = service.calibrate(
                LeagueCode.ELITESERIEN,
                MarketCode.OVER_2_5_GOALS,
                "model-v1",
                qualityDate,
                new BigDecimal("0.620000")
        );

        assertThat(result.rawProbability()).isEqualByComparingTo("0.620000");
        assertThat(result.calibratedProbability()).isEqualByComparingTo("0.620000");
        assertThat(result.confidenceBand()).isEqualTo(PredictionConfidenceBand.UNRATED);
        assertThat(result.modelQualitySnapshot()).isNull();
    }
}
