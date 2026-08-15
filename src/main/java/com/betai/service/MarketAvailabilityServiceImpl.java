package com.betai.service;

import com.betai.domain.league.LeagueCode;
import com.betai.domain.market.MarketCode;
import com.betai.repository.LeagueSeasonMarketAvailabilityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MarketAvailabilityServiceImpl implements MarketAvailabilityService {

    private final LeagueSeasonMarketAvailabilityRepository marketAvailabilityRepository;

    @Override
    public boolean isMarketAvailable(LeagueCode leagueCode, String seasonLabel, MarketCode marketCode) {
        if (leagueCode == null || marketCode == null || !StringUtils.hasText(seasonLabel)) {
            return true;
        }
        return marketAvailabilityRepository
                .findByLeague_CodeAndSeasonLabelAndMarketCode(leagueCode, seasonLabel, marketCode)
                .map(com.betai.domain.source.LeagueSeasonMarketAvailability::isAvailable)
                .orElse(true);
    }
}
