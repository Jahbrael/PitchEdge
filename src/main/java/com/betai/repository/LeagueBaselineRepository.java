package com.betai.repository;

import com.betai.domain.feature.LeagueBaseline;
import com.betai.domain.league.LeagueCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface LeagueBaselineRepository extends JpaRepository<LeagueBaseline, UUID> {

    Optional<LeagueBaseline> findByLeague_CodeAndSeasonLabelAndCalculationDate(
            LeagueCode leagueCode,
            String seasonLabel,
            LocalDate calculationDate
    );

    Optional<LeagueBaseline> findByLeague_CodeAndCalculationDateAndSeasonWindowKey(
            LeagueCode leagueCode,
            LocalDate calculationDate,
            String seasonWindowKey
    );
}
