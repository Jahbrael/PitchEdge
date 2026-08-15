package com.betai.repository;

import com.betai.domain.league.LeagueCode;
import com.betai.domain.market.MarketCode;
import com.betai.domain.quality.ModelQualitySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ModelQualitySnapshotRepository extends JpaRepository<ModelQualitySnapshot, UUID> {

    Optional<ModelQualitySnapshot> findByLeague_CodeAndMarketDefinition_CodeAndModelVersionAndQualityDate(
            LeagueCode leagueCode,
            MarketCode marketCode,
            String modelVersion,
            LocalDate qualityDate
    );

    Optional<ModelQualitySnapshot> findFirstByLeague_CodeAndMarketDefinition_CodeAndModelVersionAndQualityDateLessThanEqualOrderByQualityDateDesc(
            LeagueCode leagueCode,
            MarketCode marketCode,
            String modelVersion,
            LocalDate qualityDate
    );

    List<ModelQualitySnapshot> findByLeague_CodeAndModelVersionAndQualityDateOrderByMarketDefinition_CodeAsc(
            LeagueCode leagueCode,
            String modelVersion,
            LocalDate qualityDate
    );

    List<ModelQualitySnapshot> findTop100ByModelVersionOrderByQualityDateDescLeague_CodeAscMarketDefinition_CodeAsc(
            String modelVersion
    );

    boolean existsByModelVersionAndQualityDateLessThanEqual(String modelVersion, LocalDate qualityDate);
}
