package com.betai.repository;

import com.betai.domain.statistics.EventStatistic;
import com.betai.domain.source.ExternalSourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface EventStatisticRepository extends JpaRepository<EventStatistic, UUID> {

    @Query("""
            select es
            from EventStatistic es
            where es.match.id = :matchId
              and ((:teamId is null and es.team is null) or es.team.id = :teamId)
              and es.statisticCode = :statisticCode
              and coalesce(es.period, 'FULL_TIME') = coalesce(:period, 'FULL_TIME')
              and es.sourceType = :sourceType
            """)
    Optional<EventStatistic> findStatistic(
            @Param("matchId") UUID matchId,
            @Param("teamId") UUID teamId,
            @Param("statisticCode") String statisticCode,
            @Param("period") String period,
            @Param("sourceType") ExternalSourceType sourceType
    );

    default Optional<EventStatistic> findTheSportsDbStatistic(
            UUID matchId,
            UUID teamId,
            String statisticCode,
            String period
    ) {
        return findStatistic(matchId, teamId, statisticCode, period, ExternalSourceType.THESPORTSDB);
    }

    @Query("""
            select count(distinct es.match.id)
            from EventStatistic es
            where es.match.league.code = :leagueCode
              and es.match.seasonLabel = :seasonLabel
            """)
    long countDistinctMatchesWithAnyStatistic(
            @Param("leagueCode") com.betai.domain.league.LeagueCode leagueCode,
            @Param("seasonLabel") String seasonLabel
    );

    @Query("""
            select count(distinct es.match.id)
            from EventStatistic es
            where es.match.league.code = :leagueCode
              and es.match.seasonLabel = :seasonLabel
              and es.statisticCode = :statisticCode
            """)
    long countDistinctMatchesWithStatistic(
            @Param("leagueCode") com.betai.domain.league.LeagueCode leagueCode,
            @Param("seasonLabel") String seasonLabel,
            @Param("statisticCode") String statisticCode
    );
}
