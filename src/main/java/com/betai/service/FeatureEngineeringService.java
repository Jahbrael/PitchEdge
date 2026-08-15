package com.betai.service;

import com.betai.api.dto.DailyFeatureGenerationRequest;
import com.betai.api.dto.DailyFeatureGenerationResponse;
import com.betai.api.dto.LeagueBaselineResponse;
import com.betai.api.dto.TeamFeatureSnapshotResponse;
import com.betai.domain.league.LeagueCode;

import java.time.LocalDate;
import java.util.List;

public interface FeatureEngineeringService {

    DailyFeatureGenerationResponse generateFeatures(DailyFeatureGenerationRequest request);

    LeagueBaselineResponse getLeagueBaseline(LeagueCode leagueCode, LocalDate calculationDate);

    List<TeamFeatureSnapshotResponse> listTeamFeatures(LeagueCode leagueCode, LocalDate calculationDate);
}
