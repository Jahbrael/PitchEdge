package com.betai.api.dto;

import com.betai.domain.league.LeagueCode;
import com.betai.domain.market.MarketCode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record OddsImportItem(
        UUID matchId,
        LeagueCode leagueCode,
        LocalDate matchDate,
        String homeTeam,
        String awayTeam,
        @NotNull MarketCode marketCode,
        @NotBlank @Size(max = 64) String bookmakerCode,
        @Size(max = 128) String bookmakerName,
        @NotNull @DecimalMin(value = "1.0001") BigDecimal decimalOdds,
        OffsetDateTime capturedAt,
        @Size(max = 160) String sourceName,
        @Size(max = 500) String sourceUrl,
        @Size(max = 500) String rawPayloadReference
) {
}
