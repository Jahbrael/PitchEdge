package com.betai.service;

import com.betai.api.dto.UserSavedBatchItemResponse;
import com.betai.api.dto.UserSavedBatchResponse;
import com.betai.api.dto.PredictionBatchResponse;
import com.betai.api.dto.PredictionResponse;
import com.betai.api.dto.PredictionSelectionResponse;
import com.betai.domain.history.UserSavedBatch;
import com.betai.domain.history.UserSavedBatchItem;
import com.betai.domain.market.MarketCode;
import com.betai.domain.odds.ValueRating;
import com.betai.domain.prediction.PredictionConfidenceBand;
import com.betai.domain.user.User;
import com.betai.repository.PredictionSelectionRepository;
import com.betai.repository.UserRepository;
import com.betai.repository.UserSavedBatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserHistoryService {

    private final UserRepository userRepository;
    private final UserSavedBatchRepository userSavedBatchRepository;
    private final PredictionSelectionRepository predictionSelectionRepository;
    private final Clock clock;

    @Transactional
    public void savePredictionResponse(UUID userId, PredictionResponse response) {
        User user = userRepository.findById(userId).orElseThrow();

        for (PredictionBatchResponse batchResponse : response.batches()) {
            UserSavedBatch batch = new UserSavedBatch()
                    .setUser(user)
                    .setBatchName("Batch " + batchResponse.batchNumber() + " - " + response.input().fixtureDateFrom());
                    
            for (PredictionSelectionResponse selectionResponse : batchResponse.selections()) {
                UserSavedBatchItem item = new UserSavedBatchItem()
                        .setPredictionSelection(predictionSelectionRepository.findById(selectionResponse.selectionId()).orElse(null))
                        .setMatchId(selectionResponse.matchId())
                        .setLeagueCode(selectionResponse.leagueCode())
                        .setFixture(selectionResponse.fixture())
                        .setKickoffAt(selectionResponse.kickoffAt())
                        .setMarketCode(MarketCode.valueOf(selectionResponse.marketCode()))
                        .setMarketName(selectionResponse.marketName())
                        .setPredictedValue(selectionResponse.predictedValue())
                        .setTeamOrPlayer(selectionResponse.teamOrPlayer())
                        .setRawModelProbability(selectionResponse.rawModelProbability())
                        .setCalibratedProbability(selectionResponse.calibratedProbability())
                        .setTunedProbability(selectionResponse.tunedModelProbability() == null
                                ? selectionResponse.probability()
                                : selectionResponse.tunedModelProbability())
                        .setConfidenceBand(confidenceBand(selectionResponse.confidenceBand()))
                        .setModelQualitySampleSize(selectionResponse.modelQualitySampleSize())
                        .setModelQualityCalibrationError(selectionResponse.modelQualityCalibrationError())
                        .setDataQualityScore(selectionResponse.dataQualityScore())
                        .setCalibrationStatus(selectionResponse.calibrationStatus())
                        .setDecimalOdds(selectionResponse.decimalOdds())
                        .setBookmakerImpliedProbability(selectionResponse.bookmakerImpliedProbability())
                        .setProbabilityEdge(selectionResponse.probabilityEdge())
                        .setExpectedValue(selectionResponse.expectedValue())
                        .setValueRating(valueRating(selectionResponse.valueRating()))
                        .setRankingScore(selectionResponse.rankingScore())
                        .setReason(selectionResponse.reason())
                        .setModelVersion(selectionResponse.modelVersion())
                        .setGeneratedAt(OffsetDateTime.now(clock));
                batch.addItem(item);
            }
            if (!batch.getItems().isEmpty()) {
                userSavedBatchRepository.save(batch);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<UserSavedBatchResponse> getUserHistory(UUID userId) {
        return userSavedBatchRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    private UserSavedBatchResponse toResponse(UserSavedBatch batch) {
        return new UserSavedBatchResponse(
                batch.getId(),
                batch.getBatchName(),
                batch.getCreatedAt(),
                batch.getItems().stream()
                        .map(this::toItemResponse)
                        .toList()
        );
    }

    private UserSavedBatchItemResponse toItemResponse(UserSavedBatchItem item) {
        return new UserSavedBatchItemResponse(
                item.getId(),
                item.getPredictionSelection() == null ? null : item.getPredictionSelection().getId(),
                item.getMatchId(),
                item.getLeagueCode(),
                item.getFixture(),
                item.getKickoffAt(),
                item.getMarketCode() == null ? null : item.getMarketCode().name(),
                item.getMarketName(),
                item.getPredictedValue(),
                item.getTeamOrPlayer(),
                item.getRawModelProbability(),
                item.getCalibratedProbability(),
                item.getTunedProbability(),
                item.getConfidenceBand() == null ? null : item.getConfidenceBand().name(),
                item.getDataQualityScore(),
                item.getModelQualitySampleSize(),
                item.getCalibrationStatus(),
                item.getModelQualitySampleSize(),
                item.getModelQualityCalibrationError(),
                item.getDecimalOdds(),
                item.getBookmakerImpliedProbability(),
                item.getProbabilityEdge(),
                item.getExpectedValue(),
                item.getValueRating() == null ? null : item.getValueRating().name(),
                item.getRankingScore(),
                item.getReason(),
                item.getModelVersion(),
                item.getGeneratedAt()
        );
    }

    private PredictionConfidenceBand confidenceBand(String value) {
        return value == null ? PredictionConfidenceBand.UNRATED : PredictionConfidenceBand.valueOf(value);
    }

    private ValueRating valueRating(String value) {
        return value == null ? ValueRating.NO_ODDS : ValueRating.valueOf(value);
    }
}
