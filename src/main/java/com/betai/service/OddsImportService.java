package com.betai.service;

import com.betai.api.dto.OddsImportRequest;
import com.betai.api.dto.OddsImportResponse;
import com.betai.api.dto.OddsSnapshotResponse;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.market.MarketCode;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface OddsImportService {

    OddsImportResponse importOdds(OddsImportRequest request);

    List<OddsSnapshotResponse> findSnapshots(
            LeagueCode leagueCode,
            MarketCode marketCode,
            UUID matchId,
            LocalDate fromDate,
            LocalDate toDate,
            int limit
    );
}
