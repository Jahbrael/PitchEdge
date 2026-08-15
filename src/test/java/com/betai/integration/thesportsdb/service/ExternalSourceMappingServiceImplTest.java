package com.betai.integration.thesportsdb.service;

import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.source.ExternalEntityType;
import com.betai.domain.source.ExternalMappingStatus;
import com.betai.domain.source.ExternalSourceMapping;
import com.betai.domain.source.ExternalSourceType;
import com.betai.repository.ExternalSourceMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalSourceMappingServiceImplTest {

    @Mock
    private ExternalSourceMappingRepository externalSourceMappingRepository;

    private ExternalSourceMappingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ExternalSourceMappingServiceImpl(externalSourceMappingRepository);
    }

    @Test
    void createsResolvedMappingWithoutReplacingInternalId() {
        League league = league();
        UUID teamId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        when(externalSourceMappingRepository.findBySourceTypeAndEntityTypeAndExternalEntityId(
                ExternalSourceType.THESPORTSDB,
                ExternalEntityType.TEAM,
                "133604"
        )).thenReturn(Optional.empty());
        when(externalSourceMappingRepository.save(any(ExternalSourceMapping.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ExternalSourceMapping mapping = service.markResolved(
                ExternalSourceType.THESPORTSDB,
                ExternalEntityType.TEAM,
                "133604",
                teamId,
                league,
                "2026",
                "Arsenal"
        );

        assertThat(mapping.getSourceType()).isEqualTo(ExternalSourceType.THESPORTSDB);
        assertThat(mapping.getEntityType()).isEqualTo(ExternalEntityType.TEAM);
        assertThat(mapping.getExternalEntityId()).isEqualTo("133604");
        assertThat(mapping.getInternalEntityId()).isEqualTo(teamId);
        assertThat(mapping.getLeague()).isSameAs(league);
        assertThat(mapping.getStatus()).isEqualTo(ExternalMappingStatus.RESOLVED);
        assertThat(mapping.getUnresolvedReason()).isNull();
    }

    @Test
    void updatesExistingUnresolvedMappingWhenItBecomesResolved() {
        League league = league();
        ExternalSourceMapping existing = new ExternalSourceMapping()
                .setSourceType(ExternalSourceType.THESPORTSDB)
                .setEntityType(ExternalEntityType.TEAM)
                .setExternalEntityId("999")
                .setStatus(ExternalMappingStatus.UNRESOLVED)
                .setUnresolvedReason("No alias matched");
        when(externalSourceMappingRepository.findBySourceTypeAndEntityTypeAndExternalEntityId(
                ExternalSourceType.THESPORTSDB,
                ExternalEntityType.TEAM,
                "999"
        )).thenReturn(Optional.of(existing));
        when(externalSourceMappingRepository.save(any(ExternalSourceMapping.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UUID teamId = UUID.fromString("00000000-0000-0000-0000-000000000004");

        ExternalSourceMapping mapping = service.markResolved(
                ExternalSourceType.THESPORTSDB,
                ExternalEntityType.TEAM,
                "999",
                teamId,
                league,
                "2026",
                "Resolved Team"
        );

        ArgumentCaptor<ExternalSourceMapping> mappingCaptor = ArgumentCaptor.forClass(ExternalSourceMapping.class);
        verify(externalSourceMappingRepository).save(mappingCaptor.capture());

        assertThat(mapping).isSameAs(existing);
        assertThat(mappingCaptor.getValue()).isSameAs(existing);
        assertThat(existing.getInternalEntityId()).isEqualTo(teamId);
        assertThat(existing.getStatus()).isEqualTo(ExternalMappingStatus.RESOLVED);
        assertThat(existing.getUnresolvedReason()).isNull();
    }

    @Test
    void recordsUnresolvedTeamForAdminReview() {
        League league = league();
        when(externalSourceMappingRepository.findBySourceTypeAndEntityTypeAndExternalEntityId(
                ExternalSourceType.THESPORTSDB,
                ExternalEntityType.TEAM,
                "404"
        )).thenReturn(Optional.empty());
        when(externalSourceMappingRepository.save(any(ExternalSourceMapping.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ExternalSourceMapping mapping = service.markUnresolved(
                ExternalSourceType.THESPORTSDB,
                ExternalEntityType.TEAM,
                "404",
                league,
                "2026",
                "Unknown FC",
                "No canonical team or alias matched."
        );

        assertThat(mapping.getInternalEntityId()).isNull();
        assertThat(mapping.getStatus()).isEqualTo(ExternalMappingStatus.UNRESOLVED);
        assertThat(mapping.getUnresolvedReason()).isEqualTo("No canonical team or alias matched.");
    }

    private League league() {
        League league = new League()
                .setCode(LeagueCode.PREMIER_LEAGUE)
                .setName("Premier League")
                .setCountry("England")
                .setTier(1)
                .setCurrentSeason("2026");
        league.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        return league;
    }
}
