package com.betai.service;

import com.betai.api.dto.PredictionResponse;
import com.betai.domain.prediction.PredictionFormRun;
import com.betai.repository.PredictionFormRunRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PredictionRunCacheService {

    private final PredictionFormRunRepository predictionFormRunRepository;
    private final ObjectMapper objectMapper;

    private final Map<UUID, PredictionResponse> runCache = Collections.synchronizedMap(
            new LinkedHashMap<UUID, PredictionResponse>(100, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<UUID, PredictionResponse> eldest) {
                    return size() > 100;
                }
            }
    );

    @Transactional
    public void put(UUID runId, PredictionResponse response) {
        if (runId != null && response != null) {
            runCache.put(runId, response);
            persistRun(runId, response);
        }
    }

    @Transactional(readOnly = true)
    public PredictionResponse get(UUID runId) {
        if (runId == null) {
            return null;
        }
        PredictionResponse cached = runCache.get(runId);
        if (cached != null) {
            return cached;
        }
        return predictionFormRunRepository.findByRequestId(runId)
                .map(this::deserialize)
                .map(response -> {
                    runCache.put(runId, response);
                    return response;
                })
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<PredictionResponse> recentRuns() {
        return predictionFormRunRepository.findTop20ByOrderByGeneratedAtDesc()
                .stream()
                .map(this::deserialize)
                .toList();
    }

    private void persistRun(UUID runId, PredictionResponse response) {
        PredictionFormRun run = predictionFormRunRepository.findByRequestId(runId)
                .orElseGet(() -> new PredictionFormRun().setRequestId(runId));
        run.setGeneratedAt(response.generatedAt())
                .setFixtureDateFrom(response.input() == null ? null : response.input().fixtureDateFrom())
                .setFixtureDateTo(response.input() == null ? null : response.input().fixtureDateTo())
                .setModelVersion(response.modelVersion())
                .setStrategy(response.input() == null || response.input().strategy() == null
                        ? null
                        : response.input().strategy().name())
                .setFixturesConsidered(response.fixturesConsidered())
                .setReturnedSelections(response.returnedSelections())
                .setStatus(response.status() == null ? null : response.status().name())
                .setResponseJson(serialize(response));
        predictionFormRunRepository.save(run);
    }

    private String serialize(PredictionResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize prediction run response.", exception);
        }
    }

    private PredictionResponse deserialize(PredictionFormRun run) {
        try {
            return objectMapper.readValue(run.getResponseJson(), PredictionResponse.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize prediction run " + run.getRequestId() + ".", exception);
        }
    }
}
