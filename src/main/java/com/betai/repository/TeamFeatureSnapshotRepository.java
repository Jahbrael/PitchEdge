package com.betai.repository;

import com.betai.domain.feature.TeamFeatureSnapshot;
import com.betai.domain.league.LeagueCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamFeatureSnapshotRepository extends JpaRepository<TeamFeatureSnapshot, UUID> {

    Optional<TeamFeatureSnapshot> findByLeague_CodeAndTeam_IdAndSeasonLabelAndCalculationDate(
            LeagueCode leagueCode,
            UUID teamId,
            String seasonLabel,
            LocalDate calculationDate
    );

    List<TeamFeatureSnapshot> findByLeague_CodeAndSeasonLabelAndCalculationDateOrderByTeam_CanonicalNameAsc(
            LeagueCode leagueCode,
            String seasonLabel,
            LocalDate calculationDate
    );

    Optional<TeamFeatureSnapshot> findByLeague_CodeAndTeam_IdAndCalculationDateAndSeasonWindowKey(
            LeagueCode leagueCode,
            UUID teamId,
            LocalDate calculationDate,
            String seasonWindowKey
    );

    List<TeamFeatureSnapshot> findByLeague_CodeAndCalculationDateAndSeasonWindowKeyOrderByTeam_CanonicalNameAsc(
            LeagueCode leagueCode,
            LocalDate calculationDate,
            String seasonWindowKey
    );
}
