package com.betai.domain.source;

import com.betai.domain.league.LeagueCode;

public record SharpApiLeagueSource(
        LeagueCode leagueCode,
        String sportKey
) {
}
