package com.betai.integration.thesportsdb.service;

import com.betai.domain.snapshot.RawSnapshot;
import com.betai.domain.snapshot.ScrapeStatus;
import com.betai.integration.thesportsdb.TheSportsDbProperties;
import com.betai.integration.thesportsdb.client.TheSportsDbClientResponse;
import com.betai.integration.thesportsdb.client.TheSportsDbSecretRedactor;
import com.betai.repository.RawSnapshotRepository;
import com.betai.scraping.HashingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TheSportsDbSnapshotServiceImpl implements TheSportsDbSnapshotService {

    private static final String DEFAULT_PARSER_VERSION = "thesportsdb-v2-json-v1";

    private final RawSnapshotRepository rawSnapshotRepository;
    private final HashingService hashingService;
    private final ObjectMapper objectMapper;
    private final TheSportsDbProperties properties;
    private final TheSportsDbSecretRedactor secretRedactor;

    @Override
    public RawSnapshot persist(TheSportsDbClientResponse response, TheSportsDbSnapshotMetadata metadata) {
        String payload = response.rawJson() == null ? "" : response.rawJson();
        String checksum = hashingService.sha256(payload);
        LocalDate snapshotDate = response.retrievedAt().toLocalDate();
        Optional<RawSnapshot> existing = findExisting(metadata.sourceTarget().getId(), snapshotDate, checksum);
        if (existing.isPresent()) {
            return existing.get();
        }

        return rawSnapshotRepository.save(new RawSnapshot()
                .setSourceTarget(metadata.sourceTarget())
                .setLeague(metadata.league())
                .setSnapshotDate(snapshotDate)
                .setSourceUrl(sourceUrl(response.path()))
                .setScrapeStatus(ScrapeStatus.SUCCESS)
                .setHttpStatusCode(response.statusCode())
                .setFetchedAt(response.retrievedAt())
                .setDurationMs(0L)
                .setChecksumSha256(checksum)
                .setContentType("application/json")
                .setContentLength((long) payload.getBytes(StandardCharsets.UTF_8).length)
                .setRawPayload(payload)
                .setEndpointName(response.endpoint().name())
                .setRequestParametersJson(requestParametersJson(metadata.requestParameters()))
                .setExternalEntityId(truncate(metadata.externalEntityId(), 160))
                .setExternalLeagueId(truncate(metadata.externalLeagueId(), 160))
                .setSourceSeason(truncate(metadata.season(), 64))
                .setExternalFixtureId(truncate(metadata.externalFixtureId(), 160))
                .setExternalEventId(truncate(metadata.externalEventId(), 160))
                .setParserVersion(StringUtils.hasText(metadata.parserVersion())
                        ? truncate(metadata.parserVersion(), 80)
                        : DEFAULT_PARSER_VERSION)
                .setProcessingStatus(truncate(metadata.processingStatus(), 40))
                .setProcessingErrorSummary(truncate(metadata.processingErrorSummary(), 1000)));
    }

    private Optional<RawSnapshot> findExisting(UUID sourceTargetId, LocalDate snapshotDate, String checksum) {
        if (sourceTargetId == null) {
            return Optional.empty();
        }
        return rawSnapshotRepository.findBySourceTarget_IdAndSnapshotDateAndChecksumSha256(
                sourceTargetId,
                snapshotDate,
                checksum
        );
    }

    private String requestParametersJson(Map<String, String> requestParameters) {
        if (requestParameters == null || requestParameters.isEmpty()) {
            return "{}";
        }
        try {
            return secretRedactor.redact(objectMapper.writeValueAsString(requestParameters));
        } catch (JsonProcessingException exception) {
            return "{\"serialization\":\"failed\"}";
        }
    }

    private String sourceUrl(String path) {
        String baseUrl = StringUtils.hasText(properties.baseUrl())
                ? properties.baseUrl().trim().replaceAll("/+$", "")
                : "https://www.thesportsdb.com/api/v2/json";
        return secretRedactor.redact(baseUrl + "/" + path);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
