package com.betai.service;

import com.betai.domain.league.LeagueCode;
import com.betai.domain.market.MarketCode;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface ModelTuningService {

    ModelTuningResult tune(
            LeagueCode leagueCode,
            MarketCode marketCode,
            String modelVersion,
            LocalDate profileDate,
            BigDecimal calibratedProbability
    );
}
