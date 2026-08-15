package com.betai.repository;

import com.betai.domain.extraction.ExtractionValidationError;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExtractionValidationErrorRepository extends JpaRepository<ExtractionValidationError, UUID> {

    List<ExtractionValidationError> findTop100ByExtractionRun_IdOrderByRowNumberAsc(UUID extractionRunId);
}
