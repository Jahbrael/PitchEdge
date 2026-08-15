package com.betai.integration.thesportsdb.dto;

import java.util.List;

public record TheSportsDbTeamDto(
        String externalTeamId,
        String name,
        String shortName,
        String country,
        List<String> aliases,
        String strBadge,
        String strLogo,
        String strBanner,
        String strEquipment,
        String strFanart1
) {
}
