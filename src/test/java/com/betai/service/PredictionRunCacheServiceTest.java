package com.betai.service;

import com.betai.api.dto.PredictionResponse;
import com.betai.api.dto.PredictionResponseStatus;
import com.betai.domain.prediction.PredictionFormRun;
import com.betai.repository.PredictionFormRunRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PredictionRunCacheServiceTest {

    @Test
    void persistsAndReloadsPredictionRunsByRequestId() {
        PredictionFormRunRepository repository = mock(PredictionFormRunRepository.class);
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        PredictionRunCacheService service = new PredictionRunCacheService(repository, objectMapper);
        UUID requestId = UUID.randomUUID();
        PredictionResponse response = response(requestId);

        when(repository.findByRequestId(requestId)).thenReturn(Optional.empty());
        when(repository.save(any(PredictionFormRun.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.put(requestId, response);

        assertThat(service.get(requestId)).isEqualTo(response);
    }

    @Test
    void loadsPredictionRunFromRepositoryWhenMemoryCacheIsEmpty() throws Exception {
        PredictionFormRunRepository repository = mock(PredictionFormRunRepository.class);
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        PredictionRunCacheService service = new PredictionRunCacheService(repository, objectMapper);
        UUID requestId = UUID.randomUUID();
        PredictionResponse response = response(requestId);
        PredictionFormRun run = new PredictionFormRun()
                .setRequestId(requestId)
                .setGeneratedAt(response.generatedAt())
                .setResponseJson(objectMapper.writeValueAsString(response));

        when(repository.findByRequestId(requestId)).thenReturn(Optional.of(run));

        assertThat(service.get(requestId)).isEqualTo(response);
    }

    private static PredictionResponse response(UUID requestId) {
        return new PredictionResponse(
                requestId,
                OffsetDateTime.parse("2026-06-27T10:15:00Z"),
                null,
                "test-model",
                List.of("SCHEDULED"),
                2,
                2,
                1,
                3,
                1,
                1,
                3,
                true,
                List.of("PREMIER_LEAGUE"),
                List.of(),
                PredictionResponseStatus.COMPLETE,
                null,
                1,
                List.of(),
                List.of(),
                java.util.Map.of()
        );
    }
}
