package com.betai.repository;

import com.betai.domain.extraction.ExtractionRun;
import com.betai.domain.extraction.ExtractionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExtractionRunRepository extends JpaRepository<ExtractionRun, UUID> {

    Optional<ExtractionRun> findFirstByRawSnapshot_IdAndExtractionStatusOrderByStartedAtDesc(
            UUID rawSnapshotId,
            ExtractionStatus extractionStatus
    );

    List<ExtractionRun> findTop10ByOrderByStartedAtDesc();
}
