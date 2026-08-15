package com.betai.integration.thesportsdb.service;

import com.betai.api.dto.TheSportsDbArtworkBackfillRequest;
import com.betai.api.dto.TheSportsDbArtworkBackfillResponse;
import com.betai.domain.league.LeagueCode;

import java.util.Set;

public interface TheSportsDbArtworkBackfillService {

    default TheSportsDbArtworkBackfillResponse backfillArtwork(Set<LeagueCode> leagueCodes) {
        return backfillArtwork(new TheSportsDbArtworkBackfillRequest(leagueCodes));
    }

    TheSportsDbArtworkBackfillResponse backfillArtwork(TheSportsDbArtworkBackfillRequest request);
}
