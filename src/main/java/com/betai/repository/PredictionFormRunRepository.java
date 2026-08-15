package com.betai.repository;

import com.betai.domain.prediction.PredictionFormRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PredictionFormRunRepository extends JpaRepository<PredictionFormRun, UUID> {

    Optional<PredictionFormRun> findByRequestId(UUID requestId);

    List<PredictionFormRun> findTop20ByOrderByGeneratedAtDesc();
}
