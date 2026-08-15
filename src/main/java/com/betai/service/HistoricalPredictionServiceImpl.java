package com.betai.service;

import com.betai.api.dto.DailyFeatureGenerationRequest;
import com.betai.api.dto.HistoricalPredictionRequest;
import com.betai.api.dto.HistoricalPredictionResponse;
import com.betai.api.dto.PredictionGenerationRequest;
import com.betai.api.dto.SettlementRequest;
import com.betai.config.PredictionProperties;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.match.MatchStatus;
import com.betai.exception.InvalidRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HistoricalPredictionServiceImpl implements HistoricalPredictionService {

    private static final int MAX_MODEL_VERSION_LENGTH = 80;

    private final FeatureEngineeringService featureEngineeringService;
    private final PredictionGenerationService predictionGenerationService;
    private final SettlementService settlementService;
    private final PredictionProperties predictionProperties;
    private final Clock clock;

    @Override
    public HistoricalPredictionResponse generateHistoricalPredictions(HistoricalPredictionRequest request) {
        OffsetDateTime triggeredAt = OffsetDateTime.now(clock);
        validateDates(request.calculationDate(), request.matchDateFrom(), request.matchDateTo());

        Set<LeagueCode> leagueCodes = request.leagueCodes();
        String baseModelVersion = resolveBaseModelVersion(request.baseModelVersion());
        String historicalModelVersion = resolveHistoricalModelVersion(
                request.historicalModelVersion(),
                baseModelVersion,
                request.calculationDate()
        );
        if (historicalModelVersion.equals(baseModelVersion)) {
            throw new InvalidRequestException("historicalModelVersion must differ from baseModelVersion to avoid overwriting normal predictions.");
        }

        boolean forceRegenerateFeatures = Boolean.TRUE.equals(request.forceRegenerateFeatures());
        boolean forceRegeneratePredictions = request.forceRegeneratePredictions() == null
                || request.forceRegeneratePredictions();
        boolean settleAfterGeneration = request.settleAfterGeneration() == null
                || request.settleAfterGeneration();

        var featureResponse = featureEngineeringService.generateFeatures(new DailyFeatureGenerationRequest(
                leagueCodes,
                request.calculationDate(),
                forceRegenerateFeatures,
                request.requestedSeasonCount(),
                request.seasonSelectionMode(),
                request.customSeasonIds()
        ));
        var predictionResponse = predictionGenerationService.generatePredictions(new PredictionGenerationRequest(
                leagueCodes,
                request.calculationDate(),
                normalizeBlank(request.featureSeasonLabel()),
                request.matchDateFrom(),
                request.matchDateTo(),
                EnumSet.of(MatchStatus.FINISHED),
                historicalModelVersion,
                baseModelVersion,
                forceRegeneratePredictions,
                request.requestedSeasonCount(),
                request.seasonSelectionMode(),
                request.customSeasonIds()
        ));
        var settlementResponse = settleAfterGeneration
                ? settlementService.settlePredictions(new SettlementRequest(
                        leagueCodes,
                        LocalDate.now(clock),
                        request.matchDateFrom(),
                        request.matchDateTo(),
                        historicalModelVersion,
                        true
                ))
                : null;

        return new HistoricalPredictionResponse(
                UUID.randomUUID(),
                triggeredAt,
                request.calculationDate(),
                request.matchDateFrom(),
                request.matchDateTo(),
                baseModelVersion,
                historicalModelVersion,
                featureResponse,
                predictionResponse,
                settlementResponse,
                List.of("Replay predictions are isolated under modelVersion " + historicalModelVersion
                        + " while calibration and tuning are read from base modelVersion " + baseModelVersion + ".")
        );
    }

    private void validateDates(LocalDate calculationDate, LocalDate matchDateFrom, LocalDate matchDateTo) {
        if (calculationDate == null) {
            throw new InvalidRequestException("calculationDate is required.");
        }
        if (matchDateFrom == null || matchDateTo == null) {
            throw new InvalidRequestException("matchDateFrom and matchDateTo are required.");
        }
        if (matchDateFrom.isAfter(matchDateTo)) {
            throw new InvalidRequestException("matchDateFrom must be on or before matchDateTo.");
        }
        if (!calculationDate.isBefore(matchDateFrom)) {
            throw new InvalidRequestException("calculationDate must be before matchDateFrom for leakage-safe historical replay.");
        }
    }

    private String resolveBaseModelVersion(String requestedModelVersion) {
        String modelVersion = StringUtils.hasText(requestedModelVersion)
                ? requestedModelVersion.trim()
                : predictionProperties.defaultModelVersion();
        if (!StringUtils.hasText(modelVersion)) {
            throw new InvalidRequestException("baseModelVersion is required when no default model version is configured.");
        }
        if (modelVersion.length() > MAX_MODEL_VERSION_LENGTH) {
            throw new InvalidRequestException("baseModelVersion cannot exceed 80 characters.");
        }
        return modelVersion;
    }

    private String resolveHistoricalModelVersion(String requestedModelVersion, String baseModelVersion, LocalDate calculationDate) {
        if (StringUtils.hasText(requestedModelVersion)) {
            String modelVersion = requestedModelVersion.trim();
            if (modelVersion.length() > MAX_MODEL_VERSION_LENGTH) {
                throw new InvalidRequestException("historicalModelVersion cannot exceed 80 characters.");
            }
            return modelVersion;
        }
        String suffix = "-replay-" + calculationDate;
        if (baseModelVersion.length() + suffix.length() <= MAX_MODEL_VERSION_LENGTH) {
            return baseModelVersion + suffix;
        }
        return baseModelVersion.substring(0, MAX_MODEL_VERSION_LENGTH - suffix.length()) + suffix;
    }

    private String normalizeBlank(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
