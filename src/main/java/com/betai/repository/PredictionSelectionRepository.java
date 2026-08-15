package com.betai.repository;

import com.betai.domain.league.LeagueCode;
import com.betai.domain.market.MarketCode;
import com.betai.domain.match.MatchStatus;
import com.betai.domain.prediction.PredictionOutcome;
import com.betai.domain.prediction.PredictionSelection;
import jakarta.persistence.QueryHint;
import org.hibernate.jpa.HibernateHints;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PredictionSelectionRepository extends JpaRepository<PredictionSelection, UUID> {

    @Query("""
            select ps
            from PredictionSelection ps
            join fetch ps.match m
            join fetch m.league l
            join fetch m.homeTeam ht
            join fetch m.awayTeam at
            join fetch ps.marketDefinition md
            left join fetch ps.modelQualitySnapshot mqs
            left join fetch ps.bestOddsBookmaker bob
            left join fetch ps.bestOddsSnapshot bos
            where l.code in :leagueCodes
              and md.code in :marketCodes
              and md.enabled = true
              and m.matchDate between :fromDate and :toDate
              and m.status in :statuses
              and ps.outcome = :outcome
            order by ps.probability desc, m.kickoffAt asc
            """)
    List<PredictionSelection> findCandidateSelections(
            @Param("leagueCodes") Collection<LeagueCode> leagueCodes,
            @Param("marketCodes") Collection<MarketCode> marketCodes,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("statuses") Collection<MatchStatus> statuses,
            @Param("outcome") PredictionOutcome outcome
    );

    @Query("""
            select ps
            from PredictionSelection ps
            join fetch ps.match m
            join fetch m.league l
            join fetch m.homeTeam ht
            join fetch m.awayTeam at
            join fetch ps.marketDefinition md
            left join fetch ps.modelQualitySnapshot mqs
            left join fetch ps.bestOddsBookmaker bob
            left join fetch ps.bestOddsSnapshot bos
            where l.code in :leagueCodes
              and md.code in :marketCodes
              and md.enabled = true
              and m.matchDate between :fromDate and :toDate
              and m.status in :statuses
              and ps.outcome = :outcome
              and ps.modelVersion = :modelVersion
            order by ps.probability desc, m.kickoffAt asc
            """)
    List<PredictionSelection> findCandidateSelectionsForModel(
            @Param("leagueCodes") Collection<LeagueCode> leagueCodes,
            @Param("marketCodes") Collection<MarketCode> marketCodes,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("statuses") Collection<MatchStatus> statuses,
            @Param("outcome") PredictionOutcome outcome,
            @Param("modelVersion") String modelVersion
    );

    @Query("""
            select ps
            from PredictionSelection ps
            join fetch ps.match m
            join fetch m.league l
            join fetch m.homeTeam ht
            join fetch m.awayTeam at
            join fetch ps.marketDefinition md
            left join fetch ps.modelQualitySnapshot mqs
            left join fetch ps.bestOddsBookmaker bob
            left join fetch ps.bestOddsSnapshot bos
            where l.code in :leagueCodes
              and md.code in :marketCodes
              and md.enabled = true
              and m.matchDate between :fromDate and :toDate
              and m.status in :statuses
              and ps.outcome in :outcomes
              and ps.modelVersion = :modelVersion
            order by ps.probability desc, m.kickoffAt asc
            """)
    List<PredictionSelection> findCandidateSelectionsForModelAndOutcomes(
            @Param("leagueCodes") Collection<LeagueCode> leagueCodes,
            @Param("marketCodes") Collection<MarketCode> marketCodes,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("statuses") Collection<MatchStatus> statuses,
            @Param("outcomes") Collection<PredictionOutcome> outcomes,
            @Param("modelVersion") String modelVersion
    );

    List<PredictionSelection> findByMatch_IdAndMarketDefinition_EnabledTrueOrderByProbabilityDesc(UUID matchId);
    List<PredictionSelection> findByMatch_IdInAndMarketDefinition_EnabledTrue(List<UUID> matchIds);

    List<PredictionSelection> findByMatch_IdAndMarketDefinition_Id(UUID matchId, UUID marketDefinitionId);

    @Query("""
            select ps
            from PredictionSelection ps
            join fetch ps.match m
            join fetch m.league l
            join fetch m.homeTeam ht
            join fetch m.awayTeam at
            join fetch ps.marketDefinition md
            left join fetch ps.modelQualitySnapshot mqs
            left join fetch ps.bestOddsBookmaker bob
            left join fetch ps.bestOddsSnapshot bos
            where m.matchDate between :fromDate and :toDate
              and md.enabled = true
              and ps.outcome = :outcome
            order by ps.probability desc, m.kickoffAt asc
            """)
    List<PredictionSelection> findSelectionsBetweenDates(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("outcome") PredictionOutcome outcome,
            Pageable pageable
    );

    @Query("""
            select ps
            from PredictionSelection ps
            join fetch ps.match m
            join fetch m.league l
            join fetch m.homeTeam ht
            join fetch m.awayTeam at
            join fetch ps.marketDefinition md
            left join fetch ps.modelQualitySnapshot mqs
            join fetch ps.bestOddsBookmaker bob
            join fetch ps.bestOddsSnapshot bos
            where m.matchDate between :fromDate and :toDate
              and md.enabled = true
              and ps.bestOddsSnapshot is not null
              and ps.valueEdge is not null
              and ps.valueEdge > 0
            order by ps.valueEdge desc, ps.probability desc, m.kickoffAt asc
            """)
    List<PredictionSelection> findPositiveValueSelectionsBetweenDates(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable
    );

    @Query("""
            select ps
            from PredictionSelection ps
            join fetch ps.match m
            join fetch m.league l
            join fetch m.homeTeam ht
            join fetch m.awayTeam at
            left join fetch m.statistics ms
            join fetch ps.marketDefinition md
            where l.code = :leagueCode
              and m.matchDate between :fromDate and :toDate
              and m.status = com.betai.domain.match.MatchStatus.FINISHED
              and ps.modelVersion = :modelVersion
              and (:forceResettle = true or ps.outcome = com.betai.domain.prediction.PredictionOutcome.PENDING)
            order by m.matchDate asc, m.kickoffAt asc, md.code asc
            """)
    List<PredictionSelection> findSelectionsForSettlement(
            @Param("leagueCode") LeagueCode leagueCode,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("modelVersion") String modelVersion,
            @Param("forceResettle") boolean forceResettle
    );

    @Query("""
            select ps
            from PredictionSelection ps
            join fetch ps.match m
            join fetch m.league l
            join fetch ps.marketDefinition md
            where l.code = :leagueCode
              and m.matchDate <= :toDate
              and m.status = com.betai.domain.match.MatchStatus.FINISHED
              and ps.modelVersion = :modelVersion
              and ps.outcome in (
                com.betai.domain.prediction.PredictionOutcome.WON,
                com.betai.domain.prediction.PredictionOutcome.LOST,
                com.betai.domain.prediction.PredictionOutcome.VOID
              )
            order by md.code asc
            """)
    List<PredictionSelection> findSettledSelectionsForAccuracy(
            @Param("leagueCode") LeagueCode leagueCode,
            @Param("toDate") LocalDate toDate,
            @Param("modelVersion") String modelVersion
    );

    @Query("""
            select ps
            from PredictionSelection ps
            join fetch ps.match m
            join fetch m.league l
            join fetch ps.marketDefinition md
            where l.code = :leagueCode
              and m.matchDate <= :qualityDate
              and m.status = com.betai.domain.match.MatchStatus.FINISHED
              and ps.modelVersion = :modelVersion
              and ps.outcome in (
                com.betai.domain.prediction.PredictionOutcome.WON,
                com.betai.domain.prediction.PredictionOutcome.LOST,
                com.betai.domain.prediction.PredictionOutcome.VOID
              )
            order by md.code asc
            """)
    @QueryHints(@QueryHint(name = HibernateHints.HINT_READ_ONLY, value = "true"))
    List<PredictionSelection> findSettledSelectionsForQuality(
            @Param("leagueCode") LeagueCode leagueCode,
            @Param("qualityDate") LocalDate qualityDate,
            @Param("modelVersion") String modelVersion
    );

    @Query("""
            select ps
            from PredictionSelection ps
            join fetch ps.match m
            join fetch m.league l
            join fetch ps.marketDefinition md
            left join fetch ps.bestOddsBookmaker bob
            left join fetch ps.bestOddsSnapshot bos
            where l.code in :leagueCodes
              and m.matchDate between :fromDate and :toDate
              and m.status = com.betai.domain.match.MatchStatus.FINISHED
              and ps.modelVersion = :modelVersion
              and ps.outcome in (
                com.betai.domain.prediction.PredictionOutcome.WON,
                com.betai.domain.prediction.PredictionOutcome.LOST,
                com.betai.domain.prediction.PredictionOutcome.VOID
              )
            order by l.code asc, md.code asc, m.matchDate asc
            """)
    @QueryHints(@QueryHint(name = HibernateHints.HINT_READ_ONLY, value = "true"))
    List<PredictionSelection> findSettledSelectionsForBacktest(
            @Param("leagueCodes") Collection<LeagueCode> leagueCodes,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("modelVersion") String modelVersion
    );

    @Query("""
            select ps
            from PredictionSelection ps
            join fetch ps.match m
            join fetch ps.marketDefinition md
            where m.id in :matchIds
              and ps.modelVersion = :modelVersion
            """)
    List<PredictionSelection> findExistingForMatchesAndModel(
            @Param("matchIds") Collection<UUID> matchIds,
            @Param("modelVersion") String modelVersion
    );

    @Query("""
            select count(ps)
            from PredictionSelection ps
            join ps.match m
            join m.league l
            join ps.marketDefinition md
            where l.code = :leagueCode
              and m.matchDate between :fromDate and :toDate
              and m.status in :statuses
              and ps.modelVersion = :modelVersion
              and md.enabled = true
            """)
    long countStoredEnabledSelectionsForGenerationWindow(
            @Param("leagueCode") LeagueCode leagueCode,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("statuses") Collection<MatchStatus> statuses,
            @Param("modelVersion") String modelVersion
    );

    Optional<PredictionSelection> findByMatch_IdAndMarketDefinition_IdAndModelVersion(
            UUID matchId,
            UUID marketDefinitionId,
            String modelVersion
    );

    long countByOutcome(PredictionOutcome outcome);

    long countByBestOddsSnapshotIsNotNull();

    long countByExpectedValueGreaterThan(java.math.BigDecimal expectedValue);

    @Query("""
            select count(ps)
            from PredictionSelection ps
            join ps.match m
            join m.league l
            join ps.marketDefinition md
            where l.code = :leagueCode
              and md.code = :marketCode
              and ps.modelVersion = :modelVersion
              and ps.outcome in (
                com.betai.domain.prediction.PredictionOutcome.WON,
                com.betai.domain.prediction.PredictionOutcome.LOST
              )
            """)
    long countResolvedSelectionsForReadiness(
            @Param("leagueCode") LeagueCode leagueCode,
            @Param("marketCode") MarketCode marketCode,
            @Param("modelVersion") String modelVersion
    );

    @Query("""
            select count(ps)
            from PredictionSelection ps
            join ps.match m
            join m.league l
            join ps.marketDefinition md
            where l.code = :leagueCode
              and md.code = :marketCode
              and ps.modelVersion = :modelVersion
              and ps.bestOddsSnapshot is not null
            """)
    long countPricedSelectionsForReadiness(
            @Param("leagueCode") LeagueCode leagueCode,
            @Param("marketCode") MarketCode marketCode,
            @Param("modelVersion") String modelVersion
    );
}
