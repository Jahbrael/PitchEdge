package com.betai.service;

import com.betai.domain.league.LeagueCode;
import com.betai.domain.market.MarketCode;
import com.betai.domain.tuning.ModelTuningProfile;
import com.betai.repository.ModelTuningProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class ModelTuningServiceImpl implements ModelTuningService {

    private static final BigDecimal MIN_PROBABILITY = new BigDecimal("0.020000");
    private static final BigDecimal MAX_PROBABILITY = new BigDecimal("0.980000");

    private final ModelTuningProfileRepository modelTuningProfileRepository;

    @Override
    public ModelTuningResult tune(
            LeagueCode leagueCode,
            MarketCode marketCode,
            String modelVersion,
            LocalDate profileDate,
            BigDecimal calibratedProbability
    ) {
        String segmentKey = TuningSegment.probabilityBand(calibratedProbability);
        ModelTuningProfile profile = modelTuningProfileRepository
                .findFirstByLeague_CodeAndMarketDefinition_CodeAndModelVersionAndSegmentKeyAndProfileDateLessThanEqualAndActiveTrueOrderByProfileDateDesc(
                        leagueCode,
                        marketCode,
                        modelVersion,
                        segmentKey,
                        profileDate
                )
                .or(() -> modelTuningProfileRepository
                        .findFirstByLeague_CodeAndMarketDefinition_CodeAndModelVersionAndSegmentKeyAndProfileDateLessThanEqualAndActiveTrueOrderByProfileDateDesc(
                                leagueCode,
                                marketCode,
                                modelVersion,
                                TuningSegment.GLOBAL,
                                profileDate
                        ))
                .orElse(null);
        if (profile == null) {
            return new ModelTuningResult(
                    scale(calibratedProbability),
                    scale(calibratedProbability),
                    BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP),
                    null,
                    "No active model tuning profile exists for this league/market/model on or before " + profileDate + "."
            );
        }

        BigDecimal tuned = clamp(calibratedProbability.add(profile.getAppliedProbabilityAdjustment()));
        return new ModelTuningResult(
                scale(calibratedProbability),
                tuned,
                profile.getAppliedProbabilityAdjustment().setScale(6, RoundingMode.HALF_UP),
                profile,
                "Applied " + TuningSegment.noteLabel(profile.getSegmentKey()) + " tuning profile "
                        + profile.getId() + " from " + profile.getProfileDate()
                        + " using adjustment " + profile.getAppliedProbabilityAdjustment().setScale(6, RoundingMode.HALF_UP) + "."
        );
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
