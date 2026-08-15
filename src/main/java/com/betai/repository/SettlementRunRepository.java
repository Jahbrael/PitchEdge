package com.betai.repository;

import com.betai.domain.settlement.SettlementRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SettlementRunRepository extends JpaRepository<SettlementRun, UUID> {

    List<SettlementRun> findTop10ByOrderByStartedAtDesc();
}
