package com.betai.repository;

import com.betai.domain.backtest.BacktestRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BacktestRunRepository extends JpaRepository<BacktestRun, UUID> {

    List<BacktestRun> findTop10ByOrderByStartedAtDesc();
}
