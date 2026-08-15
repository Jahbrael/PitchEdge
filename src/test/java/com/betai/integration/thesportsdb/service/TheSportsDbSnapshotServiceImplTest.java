package com.betai.integration.thesportsdb.service;

import com.betai.domain.league.League;
import com.betai.domain.league.LeagueCode;
import com.betai.domain.snapshot.RawSnapshot;
import com.betai.domain.snapshot.ScrapeStatus;
import com.betai.domain.source.RenderMode;
import com.betai.domain.source.SourceTarget;
import com.betai.domain.source.SourceType;
import com.betai.integration.thesportsdb.TheSportsDbProperties;
import com.betai.integration.thesportsdb.client.TheSportsDbClientResponse;
import com.betai.integration.thesportsdb.client.TheSportsDbEndpoint;
import com.betai.integration.thesportsdb.client.TheSportsDbSecretRedactor;
import com.betai.repository.RawSnapshotRepository;
import com.betai.scraping.HashingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TheSportsDbSnapshotServiceImplTest {

    private static final String API_KEY = "test-thesportsdb-api-key";

    @Mock
    private RawSnapshotRepository rawSnapshotRepository;

    private TheSportsDbSnapshotServiceImpl service;

    @BeforeEach
    void setUp() {
        TheSportsDbProperties properties = new TheSportsDbProperties(
                true,
                "https://example.test/api/v2/json",
                API_KEY,
                80,
                Duration.ofSeconds(10),
                Duration.ofSeconds(30),
                false,
                false
        );
        service = new TheSportsDbSnapshotServiceImpl(
                rawSnapshotRepository,
                new HashingService(),
                new ObjectMapper(),
                properties,
                new TheSportsDbSecretRedactor(properties)
        );
    }

    @Test
    void persistsRawJsonWithAuditMetadataWithoutAuthHeaders() {
        League league = league();
        SourceTarget sourceTarget = sourceTarget(league);
        String rawJson = "{\"events\":[{\"idEvent\":\"123\"}]}";
        TheSportsDbClientResponse response = new TheSportsDbClientResponse(
                TheSportsDbEndpoint.SCHEDULE_LEAGUE,
                "schedule/league/4328/2026",
                200,
                OffsetDateTime.parse("2026-06-20T10:15:00Z"),
                rawJson
        );
        TheSportsDbSnapshotMetadata metadata = new TheSportsDbSnapshotMetadata(
                league,
                sourceTarget,
                Map.of("leagueId", "4328", "season", "2026"),
                "123",
                "4328",
                "2026",
                "123",
                "123",
                null,
                "RAW_STORED",
                null
        );
        when(rawSnapshotRepository.findBySourceTarget_IdAndSnapshotDateAndChecksumSha256(
                sourceTarget.getId(),
                response.retrievedAt().toLocalDate(),
                new HashingService().sha256(rawJson)
        )).thenReturn(Optional.empty());
        when(rawSnapshotRepository.save(any(RawSnapshot.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RawSnapshot saved = service.persist(response, metadata);

        ArgumentCaptor<RawSnapshot> snapshotCaptor = ArgumentCaptor.forClass(RawSnapshot.class);
        verify(rawSnapshotRepository).save(snapshotCaptor.capture());
        RawSnapshot snapshot = snapshotCaptor.getValue();

        assertThat(saved).isSameAs(snapshot);
        assertThat(snapshot.getLeague()).isSameAs(league);
        assertThat(snapshot.getSourceTarget()).isSameAs(sourceTarget);
        assertThat(snapshot.getScrapeStatus()).isEqualTo(ScrapeStatus.SUCCESS);
        assertThat(snapshot.getEndpointName()).isEqualTo("SCHEDULE_LEAGUE");
        assertThat(snapshot.getRequestParametersJson()).contains("\"leagueId\":\"4328\"");
        assertThat(snapshot.getExternalEntityId()).isEqualTo("123");
        assertThat(snapshot.getExternalLeagueId()).isEqualTo("4328");
        assertThat(snapshot.getSourceSeason()).isEqualTo("2026");
        assertThat(snapshot.getExternalEventId()).isEqualTo("123");
        assertThat(snapshot.getParserVersion()).isEqualTo("thesportsdb-v2-json-v1");
        assertThat(snapshot.getProcessingStatus()).isEqualTo("RAW_STORED");
        assertThat(snapshot.getRawPayload()).isEqualTo(rawJson);
        assertThat(snapshot.getChecksumSha256()).isEqualTo(new HashingService().sha256(rawJson));
        assertThat(snapshot.getSourceUrl()).isEqualTo("https://example.test/api/v2/json/schedule/league/4328/2026");
        assertThat(snapshot.getSourceUrl()).doesNotContain(API_KEY);
        assertThat(snapshot.getResponseHeadersJson()).isNull();
    }

    @Test
    void reusesExistingSnapshotForSameSourceDateAndChecksum() {
        League league = league();
        SourceTarget sourceTarget = sourceTarget(league);
        RawSnapshot existing = new RawSnapshot().setChecksumSha256("existing");
        String rawJson = "{\"events\":[]}";
        TheSportsDbClientResponse response = new TheSportsDbClientResponse(
                TheSportsDbEndpoint.SCHEDULE_LEAGUE,
                "schedule/league/4328/2026",
                200,
                OffsetDateTime.parse("2026-06-20T10:15:00Z"),
                rawJson
        );
        when(rawSnapshotRepository.findBySourceTarget_IdAndSnapshotDateAndChecksumSha256(
                sourceTarget.getId(),
                response.retrievedAt().toLocalDate(),
                new HashingService().sha256(rawJson)
        )).thenReturn(Optional.of(existing));

        RawSnapshot saved = service.persist(response, new TheSportsDbSnapshotMetadata(
                league,
                sourceTarget,
                Map.of(),
                null,
                "4328",
                "2026",
                null,
                null,
                null,
                "RAW_STORED",
                null
        ));

        assertThat(saved).isSameAs(existing);
        verify(rawSnapshotRepository, never()).save(any(RawSnapshot.class));
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

    private SourceTarget sourceTarget(League league) {
        SourceTarget sourceTarget = new SourceTarget()
                .setLeague(league)
                .setSourceType(SourceType.MATCH_DATA)
                .setName("TheSportsDB Premier League")
                .setUrlTemplate("https://example.test/api/v2/json/schedule/league/4328/2026")
                .setRenderMode(RenderMode.STATIC_HTML)
                .setReliabilityScore(new BigDecimal("95.00"));
        sourceTarget.setId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        return sourceTarget;
    }
}
