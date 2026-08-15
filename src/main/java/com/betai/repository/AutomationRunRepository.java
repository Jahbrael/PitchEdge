package com.betai.repository;

import com.betai.domain.automation.AutomationRun;
import com.betai.domain.automation.AutomationRunStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AutomationRunRepository extends JpaRepository<AutomationRun, UUID> {

    List<AutomationRun> findTop10ByOrderByStartedAtDesc();

    Optional<AutomationRun> findTopByOrderByStartedAtDesc();

    List<AutomationRun> findByRunStatus(AutomationRunStatus runStatus);

    long countByRunStatus(AutomationRunStatus runStatus);
}
