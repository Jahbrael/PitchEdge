package com.betai.repository;

import com.betai.domain.league.LeagueCode;
import com.betai.domain.match.Match;
import com.betai.domain.match.MatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchRepository extends JpaRepository<Match, UUID> {

    @Query("""
            select m
            from FootballMatch m
            join fetch m.league l
            join fetch m.homeTeam ht
            join fetch m.awayTeam at
            where l.code in :leagueCodes
              and m.matchDate between :fromDate and :toDate
              and m.status in :statuses
            order by m.matchDate asc, m.kickoffAt asc
            """)
    List<Match> findCandidateFixtures(
            @Param("leagueCodes") Collection<LeagueCode> leagueCodes,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("statuses") Collection<MatchStatus> statuses
    );

    List<Match> findByLeague_CodeAndMatchDateBetweenOrderByKickoffAtAsc(
            LeagueCode leagueCode,
            LocalDate fromDate,
            LocalDate toDate
    );

    List<Match> findAllByLeague_CodeAndSourceFixtureKey(LeagueCode leagueCode, String sourceFixtureKey);

    default Optional<Match> findByLeague_CodeAndSourceFixtureKeySafely(LeagueCode leagueCode, String sourceFixtureKey) {
        List<Match> results = findAllByLeague_CodeAndSourceFixtureKey(leagueCode, sourceFixtureKey);
        if (results.size() > 1) {
            throw new com.betai.exception.DuplicateEntityException(
                    "Match", null, "FIXTURE", leagueCode.name(), sourceFixtureKey, results.size(),
                    "Delete duplicate match records in the database with the same source_fixture_key for this league."
            );
        }
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    List<Match> findAllByLeague_CodeAndHomeTeam_IdAndAwayTeam_IdAndKickoffAt(
            LeagueCode leagueCode,
            UUID homeTeamId,
            UUID awayTeamId,
            OffsetDateTime kickoffAt
    );

    default Optional<Match> findByLeague_CodeAndHomeTeam_IdAndAwayTeam_IdAndKickoffAtSafely(
            LeagueCode leagueCode, UUID homeTeamId, UUID awayTeamId, OffsetDateTime kickoffAt) {
        List<Match> results = findAllByLeague_CodeAndHomeTeam_IdAndAwayTeam_IdAndKickoffAt(leagueCode, homeTeamId, awayTeamId, kickoffAt);
        if (results.size() > 1) {
            throw new com.betai.exception.DuplicateEntityException(
                    "Match", null, "FIXTURE", leagueCode.name(), null, results.size(),
                    "Delete duplicate match records in the database with the exact same teams and kickoff time."
            );
        }
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    List<Match> findAllByLeague_CodeAndHomeTeam_IdAndAwayTeam_IdAndMatchDate(
            LeagueCode leagueCode,
            UUID homeTeamId,
            UUID awayTeamId,
            LocalDate matchDate
    );

    default Optional<Match> findByLeague_CodeAndHomeTeam_IdAndAwayTeam_IdAndMatchDateSafely(
            LeagueCode leagueCode, UUID homeTeamId, UUID awayTeamId, LocalDate matchDate) {
        List<Match> results = findAllByLeague_CodeAndHomeTeam_IdAndAwayTeam_IdAndMatchDate(leagueCode, homeTeamId, awayTeamId, matchDate);
        if (results.size() > 1) {
            throw new com.betai.exception.DuplicateEntityException(
                    "Match", null, "FIXTURE", leagueCode.name(), null, results.size(),
                    "Delete duplicate match records in the database with the exact same teams and match date."
            );
        }
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Query("""
            select distinct m.seasonLabel
            from FootballMatch m
            where m.league.code = :leagueCode
            order by m.seasonLabel desc
            """)
    List<String> findDistinctSeasonLabelsByLeagueCode(@Param("leagueCode") LeagueCode leagueCode);

    @Query("""
            select m.seasonLabel as seasonLabel,
                   count(m) as matchCount,
                   sum(case when m.status = com.betai.domain.match.MatchStatus.FINISHED then 1 else 0 end) as finishedCount,
                   sum(case when m.status = com.betai.domain.match.MatchStatus.SCHEDULED then 1 else 0 end) as scheduledCount,
                   min(m.matchDate) as firstMatchDate,
                   max(m.matchDate) as lastMatchDate
            from FootballMatch m
            where m.league.code = :leagueCode
            group by m.seasonLabel
            order by max(m.matchDate) desc, m.seasonLabel desc
            """)
    List<LeagueSeasonMatchSummary> summarizeSeasonsByLeagueCode(@Param("leagueCode") LeagueCode leagueCode);

    @Query("""
            select distinct m
            from FootballMatch m
            join fetch m.league l
            join fetch m.homeTeam ht
            join fetch m.awayTeam at
            left join fetch m.statistics ms
            where l.code = :leagueCode
              and m.seasonLabel in :seasonLabels
              and m.matchDate <= :calculationDate
              and m.status = com.betai.domain.match.MatchStatus.FINISHED
              and m.homeScore is not null
              and m.awayScore is not null
            order by m.matchDate asc, m.kickoffAt asc
            """)
    List<Match> findFinishedMatchesForFeatureGenerationWindow(
            @Param("leagueCode") LeagueCode leagueCode,
            @Param("seasonLabels") Collection<String> seasonLabels,
            @Param("calculationDate") LocalDate calculationDate
    );

    @Query("""
            select distinct m
            from FootballMatch m
            join fetch m.league l
            join fetch m.homeTeam ht
            join fetch m.awayTeam at
            left join fetch m.statistics ms
            where l.code = :leagueCode
              and m.matchDate between :fromDate and :toDate
              and m.status = com.betai.domain.match.MatchStatus.FINISHED
              and m.homeScore is not null
              and m.awayScore is not null
            order by m.matchDate asc, m.kickoffAt asc
            """)
    List<Match> findFinishedMatchesForFeatureGenerationDateWindow(
            @Param("leagueCode") LeagueCode leagueCode,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    @Query("""
            select distinct m
            from FootballMatch m
            join fetch m.league l
            join fetch m.homeTeam ht
            join fetch m.awayTeam at
            left join fetch m.statistics ms
            where l.code = :leagueCode
              and m.matchDate between :fromDate and :toDate
              and m.status in :statuses
            order by m.matchDate asc, m.kickoffAt asc
            """)
    List<Match> findMatchesForPredictionGeneration(
            @Param("leagueCode") LeagueCode leagueCode,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("statuses") Collection<MatchStatus> statuses
    );

    @Query("""
            select distinct m
            from FootballMatch m
            join fetch m.league l
            join fetch m.homeTeam ht
            join fetch m.awayTeam at
            left join fetch m.statistics ms
            where l.code = :leagueCode
              and m.seasonLabel = :seasonLabel
              and m.matchDate <= :calculationDate
              and m.status = com.betai.domain.match.MatchStatus.FINISHED
              and m.homeScore is not null
              and m.awayScore is not null
            order by m.matchDate asc, m.kickoffAt asc
            """)
    List<Match> findFinishedMatchesForFeatureGeneration(
            @Param("leagueCode") LeagueCode leagueCode,
            @Param("seasonLabel") String seasonLabel,
            @Param("calculationDate") LocalDate calculationDate
    );

    long countByLeague_Code(LeagueCode leagueCode);

    long countByLeague_CodeAndStatus(LeagueCode leagueCode, MatchStatus status);

    long countByStatus(MatchStatus status);

    long countByLeague_CodeAndSeasonLabel(LeagueCode leagueCode, String seasonLabel);

    long countByLeague_CodeAndSeasonLabelAndStatus(LeagueCode leagueCode, String seasonLabel, MatchStatus status);

    long countByLeague_CodeAndSeasonLabelAndStatusAndMatchDateLessThanEqual(
            LeagueCode leagueCode,
            String seasonLabel,
            MatchStatus status,
            LocalDate matchDate
    );

    @Query("""
            select count(m)
            from FootballMatch m
            where m.league.code = :leagueCode
              and m.status = com.betai.domain.match.MatchStatus.FINISHED
              and m.matchDate between :fromDate and :toDate
              and m.homeScore is not null
              and m.awayScore is not null
            """)
    long countFinishedMatchesByLeagueCodeAndMatchDateBetween(
            @Param("leagueCode") LeagueCode leagueCode,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    @Query("""
            select m
            from FootballMatch m
            join fetch m.league l
            join fetch m.homeTeam ht
            join fetch m.awayTeam at
            left join fetch m.statistics ms
            where (m.homeTeam.id = :teamId or m.awayTeam.id = :teamId)
              and m.matchDate < :beforeDate
              and m.status = com.betai.domain.match.MatchStatus.FINISHED
              and m.homeScore is not null
              and m.awayScore is not null
            order by m.matchDate desc, m.kickoffAt desc
            """)
    List<Match> findRecentFinishedMatchesByTeamId(@Param("teamId") UUID teamId, @Param("beforeDate") LocalDate beforeDate);

    @Query("""
            select m
            from FootballMatch m
            join fetch m.league l
            join fetch m.homeTeam ht
            join fetch m.awayTeam at
            left join fetch m.statistics ms
            where ((m.homeTeam.id = :homeTeamId and m.awayTeam.id = :awayTeamId)
               or (m.homeTeam.id = :awayTeamId and m.awayTeam.id = :homeTeamId))
              and m.matchDate < :beforeDate
              and m.status = com.betai.domain.match.MatchStatus.FINISHED
              and m.homeScore is not null
              and m.awayScore is not null
            order by m.matchDate desc, m.kickoffAt desc
            """)
    List<Match> findHeadToHeadMatches(@Param("homeTeamId") UUID homeTeamId, @Param("awayTeamId") UUID awayTeamId, @Param("beforeDate") LocalDate beforeDate);

    @Query("""
            select distinct m
            from FootballMatch m
            join fetch m.league l
            join fetch m.homeTeam ht
            join fetch m.awayTeam at
            where (ht.id in :teamIds or at.id in :teamIds)
              and m.kickoffAt < :beforeKickoff
              and m.status = com.betai.domain.match.MatchStatus.FINISHED
              and m.homeScore is not null
              and m.awayScore is not null
            order by m.kickoffAt desc
            """)
    List<Match> findFinishedMatchesForFixtureIndicators(
            @Param("teamIds") Collection<UUID> teamIds,
            @Param("beforeKickoff") OffsetDateTime beforeKickoff
    );

    @Query("""
            select distinct m
            from FootballMatch m
            join fetch m.league l
            join fetch m.homeTeam ht
            join fetch m.awayTeam at
            where l.code in :leagueCodes
              and m.seasonLabel in :seasonLabels
              and m.kickoffAt < :beforeKickoff
              and m.status = com.betai.domain.match.MatchStatus.FINISHED
              and m.homeScore is not null
              and m.awayScore is not null
            order by m.kickoffAt asc
            """)
    List<Match> findFinishedLeagueMatchesForFixtureIndicators(
            @Param("leagueCodes") Collection<LeagueCode> leagueCodes,
            @Param("seasonLabels") Collection<String> seasonLabels,
            @Param("beforeKickoff") OffsetDateTime beforeKickoff
    );

    @Query("""
            select m
            from FootballMatch m
            join fetch m.league l
            join fetch m.homeTeam ht
            join fetch m.awayTeam at
            where m.id in :matchIds
            """)
    List<Match> findAllForFixtureIndicators(@Param("matchIds") Collection<UUID> matchIds);

    interface LeagueSeasonMatchSummary {
        String getSeasonLabel();

        long getMatchCount();

        long getFinishedCount();

        long getScheduledCount();

        LocalDate getFirstMatchDate();

        LocalDate getLastMatchDate();
    }
}
