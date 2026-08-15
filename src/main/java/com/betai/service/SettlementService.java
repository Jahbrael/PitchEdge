package com.betai.service;

import com.betai.api.dto.ModelAccuracyResponse;
import com.betai.api.dto.SettlementRequest;
import com.betai.api.dto.SettlementResponse;
import com.betai.domain.league.LeagueCode;

import java.time.LocalDate;
import java.util.List;

public interface SettlementService {

    SettlementResponse settlePredictions(SettlementRequest request);

    List<ModelAccuracyResponse> getAccuracy(
            LeagueCode leagueCode,
            String modelVersion,
            LocalDate accuracyDate
    );
}
