package com.betai.repository;

import com.betai.domain.league.LeagueCode;
import com.betai.domain.market.MarketCode;
import com.betai.domain.tuning.ModelTuningProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface ModelTuningProfileRepository extends JpaRepository<ModelTuningProfile, UUID> {

    Optional<ModelTuningProfile> findByLeague_CodeAndMarketDefinition_CodeAndModelVersionAndProfileDateAndSegmentKey(
            LeagueCode leagueCode,
            MarketCode marketCode,
            String modelVersion,
            LocalDate profileDate,
            String segmentKey
    );

    Optional<ModelTuningProfile> findFirstByLeague_CodeAndMarketDefinition_CodeAndModelVersionAndSegmentKeyAndProfileDateLessThanEqualAndActiveTrueOrderByProfileDateDesc(
            LeagueCode leagueCode,
            MarketCode marketCode,
            String modelVersion,
            String segmentKey,
            LocalDate profileDate
    );

    boolean existsByModelVersionAndProfileDateLessThanEqualAndActiveTrue(String modelVersion, LocalDate profileDate);

    long countByActiveTrue();
}
