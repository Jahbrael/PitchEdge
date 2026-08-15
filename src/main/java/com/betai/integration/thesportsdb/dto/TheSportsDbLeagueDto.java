package com.betai.integration.thesportsdb.dto;

public record TheSportsDbLeagueDto(
        String externalLeagueId,
        String name,
        String country,
        String strBadge,
        String strLogo,
        String strBanner,
        String strPoster,
        String strTrophy,
        String strFanart1
) {
}
