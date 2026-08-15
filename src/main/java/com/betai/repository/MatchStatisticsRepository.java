package com.betai.repository;

import com.betai.domain.statistics.MatchStatistics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MatchStatisticsRepository extends JpaRepository<MatchStatistics, UUID> {

    Optional<MatchStatistics> findByMatch_Id(UUID matchId);
}
