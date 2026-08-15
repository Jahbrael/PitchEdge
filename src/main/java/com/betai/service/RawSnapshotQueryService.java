package com.betai.service;

import com.betai.api.dto.RawSnapshotResponse;
import com.betai.domain.league.LeagueCode;

import java.time.LocalDate;
import java.util.List;

public interface RawSnapshotQueryService {

    List<RawSnapshotResponse> findRecentSnapshots(LeagueCode leagueCode, LocalDate snapshotDate);
}
