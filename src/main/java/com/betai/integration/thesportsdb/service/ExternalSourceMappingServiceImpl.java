package com.betai.integration.thesportsdb.service;

import com.betai.domain.league.League;
import com.betai.domain.source.ExternalEntityType;
import com.betai.domain.source.ExternalMappingStatus;
import com.betai.domain.source.ExternalSourceMapping;
import com.betai.domain.source.ExternalSourceType;
import com.betai.repository.ExternalSourceMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExternalSourceMappingServiceImpl implements ExternalSourceMappingService {

    private final ExternalSourceMappingRepository externalSourceMappingRepository;

    @Override
    public ExternalSourceMapping markResolved(
            ExternalSourceType sourceType,
            ExternalEntityType entityType,
            String externalEntityId,
            UUID internalEntityId,
            League league,
            String season,
            String externalName
    ) {
        ExternalSourceMapping mapping = mapping(sourceType, entityType, externalEntityId);
        mapping.setInternalEntityId(internalEntityId)
                .setLeague(league)
                .setSeason(truncate(season, 64))
                .setStatus(ExternalMappingStatus.RESOLVED)
                .setExternalName(truncate(externalName, 220))
                .setUnresolvedReason(null);
        return externalSourceMappingRepository.save(mapping);
    }

    @Override
    public ExternalSourceMapping markUnresolved(
            ExternalSourceType sourceType,
            ExternalEntityType entityType,
            String externalEntityId,
            League league,
            String season,
            String externalName,
            String unresolvedReason
    ) {
        ExternalSourceMapping mapping = mapping(sourceType, entityType, externalEntityId);
        mapping.setInternalEntityId(null)
                .setLeague(league)
                .setSeason(truncate(season, 64))
                .setStatus(ExternalMappingStatus.UNRESOLVED)
                .setExternalName(truncate(externalName, 220))
                .setUnresolvedReason(truncate(unresolvedReason, 1000));
        return externalSourceMappingRepository.save(mapping);
    }

    private ExternalSourceMapping mapping(
            ExternalSourceType sourceType,
            ExternalEntityType entityType,
            String externalEntityId
    ) {
        return externalSourceMappingRepository
                .findBySourceTypeAndEntityTypeAndExternalEntityId(sourceType, entityType, externalEntityId)
                .orElseGet(() -> new ExternalSourceMapping()
                        .setSourceType(sourceType)
                        .setEntityType(entityType)
                        .setExternalEntityId(externalEntityId));
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
