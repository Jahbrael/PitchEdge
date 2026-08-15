package com.betai.service;

import com.betai.domain.league.LeagueCode;
import com.betai.domain.market.MarketCode;

public interface MarketAvailabilityService {

    boolean isMarketAvailable(LeagueCode leagueCode, String seasonLabel, MarketCode marketCode);
}
