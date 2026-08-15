package com.betai.integration.thesportsdb.service;

import com.betai.domain.league.League;
import com.betai.domain.source.ExternalEntityType;
import com.betai.domain.source.ExternalSourceMapping;
import com.betai.domain.source.ExternalSourceType;

import java.util.UUID;

public interface ExternalSourceMappingService {

    ExternalSourceMapping markResolved(
            ExternalSourceType sourceType,
            ExternalEntityType entityType,
            String externalEntityId,
            UUID internalEntityId,
            League league,
            String season,
            String externalName
    );

    ExternalSourceMapping markUnresolved(
            ExternalSourceType sourceType,
            ExternalEntityType entityType,
            String externalEntityId,
            League league,
            String season,
            String externalName,
            String unresolvedReason
    );
}
