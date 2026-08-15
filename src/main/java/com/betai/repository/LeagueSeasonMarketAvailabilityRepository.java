package com.betai.repository;

import com.betai.domain.league.LeagueCode;
import com.betai.domain.market.MarketCode;
import com.betai.domain.source.LeagueSeasonMarketAvailability;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LeagueSeasonMarketAvailabilityRepository extends JpaRepository<LeagueSeasonMarketAvailability, UUID> {

    Optional<LeagueSeasonMarketAvailability> findByLeague_CodeAndSeasonLabelAndMarketCode(
            LeagueCode leagueCode,
            String seasonLabel,
            MarketCode marketCode
    );
}
