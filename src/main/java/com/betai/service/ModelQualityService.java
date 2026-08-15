package com.betai.service;

import com.betai.api.dto.ModelQualityGenerationRequest;
import com.betai.api.dto.ModelQualityGenerationResponse;
import com.betai.api.dto.ModelQualitySnapshotResponse;
import com.betai.domain.league.LeagueCode;

import java.time.LocalDate;
import java.util.List;

public interface ModelQualityService {

    ModelQualityGenerationResponse generateQualitySnapshots(ModelQualityGenerationRequest request);

    List<ModelQualitySnapshotResponse> getQualitySnapshots(LeagueCode leagueCode, String modelVersion, LocalDate qualityDate);
}
