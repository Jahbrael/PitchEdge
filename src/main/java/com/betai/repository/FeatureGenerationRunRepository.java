package com.betai.repository;

import com.betai.domain.feature.FeatureGenerationRun;
import com.betai.domain.feature.FeatureGenerationStatus;
import com.betai.domain.league.LeagueCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeatureGenerationRunRepository extends JpaRepository<FeatureGenerationRun, UUID> {

    Optional<FeatureGenerationRun> findFirstByLeague_CodeAndCalculationDateAndSeasonLabelAndFeatureStatusOrderByStartedAtDesc(
            LeagueCode leagueCode,
            LocalDate calculationDate,
            String seasonLabel,
            FeatureGenerationStatus status
    );

    Optional<FeatureGenerationRun> findFirstByLeague_CodeAndCalculationDateAndSeasonWindowKeyAndFeatureStatusOrderByStartedAtDesc(
            LeagueCode leagueCode,
            LocalDate calculationDate,
            String seasonWindowKey,
            FeatureGenerationStatus status
    );

    List<FeatureGenerationRun> findTop10ByOrderByStartedAtDesc();

    Optional<FeatureGenerationRun> findFirstByLeague_CodeAndFeatureStatusOrderByCalculationDateDescStartedAtDesc(
            LeagueCode leagueCode,
            FeatureGenerationStatus status
    );
}
