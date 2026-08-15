package com.betai.repository;

import com.betai.domain.league.LeagueCode;
import com.betai.domain.prediction.PredictionGenerationRun;
import com.betai.domain.prediction.PredictionGenerationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PredictionGenerationRunRepository extends JpaRepository<PredictionGenerationRun, UUID> {

    Optional<PredictionGenerationRun> findFirstByLeague_CodeAndModelVersionAndFeatureSeasonLabelAndCalculationDateAndFixtureDateFromAndFixtureDateToAndMatchStatusesAndGenerationStatusOrderByStartedAtDesc(
            LeagueCode leagueCode,
            String modelVersion,
            String featureSeasonLabel,
            LocalDate calculationDate,
            LocalDate fixtureDateFrom,
            LocalDate fixtureDateTo,
            String matchStatuses,
            PredictionGenerationStatus status
    );

    Optional<PredictionGenerationRun> findFirstByLeague_CodeAndModelVersionAndSeasonWindowKeyAndCalculationDateAndFixtureDateFromAndFixtureDateToAndMatchStatusesAndGenerationStatusOrderByStartedAtDesc(
            LeagueCode leagueCode,
            String modelVersion,
            String seasonWindowKey,
            LocalDate calculationDate,
            LocalDate fixtureDateFrom,
            LocalDate fixtureDateTo,
            String matchStatuses,
            PredictionGenerationStatus status
    );

    Optional<PredictionGenerationRun> findFirstByLeague_CodeAndModelVersionAndGenerationStatusAndFixtureDateFromLessThanEqualAndFixtureDateToGreaterThanEqualOrderByStartedAtDesc(
            LeagueCode leagueCode,
            String modelVersion,
            PredictionGenerationStatus status,
            LocalDate fixtureDateFrom,
            LocalDate fixtureDateTo
    );

    List<PredictionGenerationRun> findTop10ByOrderByStartedAtDesc();
}
