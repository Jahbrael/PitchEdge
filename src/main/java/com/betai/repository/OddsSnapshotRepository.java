package com.betai.repository;

import com.betai.domain.league.LeagueCode;
import com.betai.domain.market.MarketCode;
import com.betai.domain.odds.OddsSnapshot;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface OddsSnapshotRepository extends JpaRepository<OddsSnapshot, UUID> {

    @Query("""
            select os
            from OddsSnapshot os
            join fetch os.match m
            join fetch m.league l
            join fetch m.homeTeam ht
            join fetch m.awayTeam at
            join fetch os.marketDefinition md
            join fetch os.bookmaker b
            where (:leagueCode is null or l.code = :leagueCode)
              and (:marketCode is null or md.code = :marketCode)
              and (:matchId is null or m.id = :matchId)
              and (:fromDate is null or m.matchDate >= :fromDate)
              and (:toDate is null or m.matchDate <= :toDate)
            order by os.capturedAt desc, os.createdAt desc
            """)
    List<OddsSnapshot> findSnapshots(
            @Param("leagueCode") LeagueCode leagueCode,
            @Param("marketCode") MarketCode marketCode,
            @Param("matchId") UUID matchId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable
    );

    @Query("""
            select os
            from OddsSnapshot os
            join fetch os.bookmaker b
            where os.match.id = :matchId
              and os.marketDefinition.id = :marketDefinitionId
              and b.active = true
              and os.capturedAt = (
                    select max(latest.capturedAt)
                    from OddsSnapshot latest
                    where latest.match.id = os.match.id
                      and latest.marketDefinition.id = os.marketDefinition.id
                      and latest.bookmaker.id = os.bookmaker.id
              )
            order by os.decimalOdds desc, os.capturedAt desc
            """)
    List<OddsSnapshot> findCurrentBookmakerQuotes(
            @Param("matchId") UUID matchId,
            @Param("marketDefinitionId") UUID marketDefinitionId,
            Pageable pageable
    );

    @Query("SELECT DISTINCT os.match.id FROM OddsSnapshot os WHERE os.match.id IN :matchIds")
    java.util.Set<UUID> findMatchIdsWithOdds(@Param("matchIds") List<UUID> matchIds);
}
