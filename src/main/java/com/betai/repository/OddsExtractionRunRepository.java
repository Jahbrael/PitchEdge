package com.betai.repository;

import com.betai.domain.odds.OddsExtractionRun;
import com.betai.domain.odds.OddsExtractionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface OddsExtractionRunRepository extends JpaRepository<OddsExtractionRun, UUID> {

    Optional<OddsExtractionRun> findFirstByRawSnapshot_IdAndExtractionStatusOrderByStartedAtDesc(
            UUID rawSnapshotId,
            OddsExtractionStatus extractionStatus
    );

    List<OddsExtractionRun> findTop10ByOrderByStartedAtDesc();
}
