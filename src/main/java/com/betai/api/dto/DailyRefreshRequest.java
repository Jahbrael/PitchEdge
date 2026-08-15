package com.betai.api.dto;

import com.betai.domain.league.LeagueCode;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Set;

public record DailyRefreshRequest(
        @Size(max = 256) Set<LeagueCode> leagueCodes,
        LocalDate refreshDate,
        boolean forceRefresh
) {
}
