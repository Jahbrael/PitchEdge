package com.betai.service;

import com.betai.api.dto.LeagueMarketReadinessResponse;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.market.MarketCode;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public interface ModelReadinessService {

    List<LeagueMarketReadinessResponse> getReadiness(
            Set<LeagueCode> leagueCodes,
            Set<MarketCode> marketCodes,
            String modelVersion,
            LocalDate asOfDate
    );
}
