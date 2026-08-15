package com.betai.repository;

import com.betai.domain.backtest.BacktestMarketSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BacktestMarketSummaryRepository extends JpaRepository<BacktestMarketSummary, UUID> {

    List<BacktestMarketSummary> findByBacktestRun_IdOrderByLeague_CodeAscMarketDefinition_CodeAsc(UUID backtestRunId);
}
