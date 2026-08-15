package com.betai.repository;

import com.betai.domain.source.ExternalEntityType;
import com.betai.domain.source.ExternalMappingStatus;
import com.betai.domain.source.ExternalSourceMapping;
import com.betai.domain.source.ExternalSourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExternalSourceMappingRepository extends JpaRepository<ExternalSourceMapping, UUID> {

    Optional<ExternalSourceMapping> findBySourceTypeAndEntityTypeAndExternalEntityId(
            ExternalSourceType sourceType,
            ExternalEntityType entityType,
            String externalEntityId
    );

    Optional<ExternalSourceMapping> findBySourceTypeAndEntityTypeAndInternalEntityId(
            ExternalSourceType sourceType,
            ExternalEntityType entityType,
            UUID internalEntityId
    );

    List<ExternalSourceMapping> findBySourceTypeAndEntityTypeAndLeague_IdAndStatus(
            ExternalSourceType sourceType,
            ExternalEntityType entityType,
            UUID leagueId,
            ExternalMappingStatus status
    );

    List<ExternalSourceMapping> findBySourceTypeAndEntityTypeAndLeague_Id(
            ExternalSourceType sourceType,
            ExternalEntityType entityType,
            UUID leagueId
    );

    long countBySourceTypeAndEntityTypeAndStatus(
            ExternalSourceType sourceType,
            ExternalEntityType entityType,
            ExternalMappingStatus status
    );
}
