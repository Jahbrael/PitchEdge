package com.betai.service;

import com.betai.api.dto.TheSportsDbHealthResponse;
import com.betai.domain.source.ExternalEntityType;
import com.betai.domain.source.ExternalMappingStatus;
import com.betai.domain.source.ExternalSourceType;
import com.betai.integration.thesportsdb.TheSportsDbProperties;
import com.betai.integration.thesportsdb.client.TheSportsDbClientMetrics;
import com.betai.repository.ExternalSourceMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class TheSportsDbAdminDiagnosticsServiceImpl implements TheSportsDbAdminDiagnosticsService {

    private final TheSportsDbProperties properties;
    private final TheSportsDbClientMetrics metrics;
    private final ExternalSourceMappingRepository externalSourceMappingRepository;

    @Override
    public TheSportsDbHealthResponse health() {
        return new TheSportsDbHealthResponse(
                properties.enabled(),
                StringUtils.hasText(properties.apiKey()),
                properties.baseUrl(),
                Math.min(properties.requestsPerMinute(), 80),
                metrics.requestsInCurrentMinute(),
                metrics.tooManyRequestsCount(),
                metrics.lastSuccessfulRequestAt(),
                externalSourceMappingRepository.countBySourceTypeAndEntityTypeAndStatus(
                        ExternalSourceType.THESPORTSDB,
                        ExternalEntityType.TEAM,
                        ExternalMappingStatus.UNRESOLVED
                )
        );
    }
}
