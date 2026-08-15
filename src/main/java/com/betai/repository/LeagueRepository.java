package com.betai.repository;

import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeagueRepository extends JpaRepository<League, UUID> {

    Optional<League> findByCode(LeagueCode code);

    List<League> findByCodeInAndActiveTrue(Collection<LeagueCode> codes);

    List<League> findByCodeInAndActiveTrueAndScrapeEnabledTrue(Collection<LeagueCode> codes);

    List<League> findByActiveTrueOrderByNameAsc();

    List<League> findByActiveTrueAndScrapeEnabledTrueOrderByNameAsc();
}
