package com.betai.repository;

import com.betai.domain.league.LeagueCode;
import com.betai.domain.market.MarketCode;
import com.betai.domain.settlement.ModelAccuracyDaily;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ModelAccuracyDailyRepository extends JpaRepository<ModelAccuracyDaily, UUID> {

    Optional<ModelAccuracyDaily> findByLeague_CodeAndMarketDefinition_CodeAndModelVersionAndAccuracyDate(
            LeagueCode leagueCode,
            MarketCode marketCode,
            String modelVersion,
            LocalDate accuracyDate
    );

    List<ModelAccuracyDaily> findByLeague_CodeAndModelVersionAndAccuracyDateOrderByMarketDefinition_CodeAsc(
            LeagueCode leagueCode,
            String modelVersion,
            LocalDate accuracyDate
    );
}
