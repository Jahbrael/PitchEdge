package com.betai.domain.source;

import com.betai.domain.common.BaseEntity;
import com.betai.domain.league.League;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
@Entity
@Table(
        name = "external_source_mappings",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "ux_external_source_mappings_source_entity_external",
                        columnNames = {"source_type", "entity_type", "external_entity_id"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_external_source_mappings_internal",
                        columnList = "source_type, entity_type, internal_entity_id"
                ),
                @Index(
                        name = "idx_external_source_mappings_league_status",
                        columnList = "league_id, status"
                )
        }
)
public class ExternalSourceMapping extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 64)
    private ExternalSourceType sourceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 32)
    private ExternalEntityType entityType;

    @Column(name = "internal_entity_id")
    private UUID internalEntityId;

    @Column(name = "external_entity_id", nullable = false, length = 160)
    private String externalEntityId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "league_id")
    private League league;

    @Column(length = 64)
    private String season;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ExternalMappingStatus status = ExternalMappingStatus.UNRESOLVED;

    @Column(length = 220)
    private String externalName;

    @Column(length = 1000)
    private String unresolvedReason;
}
