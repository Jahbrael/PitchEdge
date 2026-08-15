package com.betai.repository;

import com.betai.domain.league.LeagueCode;
import com.betai.domain.source.LeagueSeasonCoverage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LeagueSeasonCoverageRepository extends JpaRepository<LeagueSeasonCoverage, UUID> {

    Optional<LeagueSeasonCoverage> findByLeague_CodeAndSeasonLabel(LeagueCode leagueCode, String seasonLabel);
}
