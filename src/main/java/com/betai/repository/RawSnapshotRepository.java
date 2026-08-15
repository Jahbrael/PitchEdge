package com.betai.repository;

import com.betai.domain.league.LeagueCode;
import com.betai.domain.snapshot.RawSnapshot;
import com.betai.domain.snapshot.ScrapeStatus;
import com.betai.domain.source.SourceType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RawSnapshotRepository extends JpaRepository<RawSnapshot, UUID> {

    Optional<RawSnapshot> findBySourceTarget_IdAndSnapshotDateAndChecksumSha256(
            UUID sourceTargetId,
            LocalDate snapshotDate,
            String checksumSha256
    );

    Optional<RawSnapshot> findFirstBySourceTarget_IdAndSnapshotDateAndScrapeStatusOrderByFetchedAtDescCreatedAtDesc(
            UUID sourceTargetId,
            LocalDate snapshotDate,
            ScrapeStatus scrapeStatus
    );

    List<RawSnapshot> findTop50ByLeague_CodeAndSnapshotDateOrderByCreatedAtDesc(
            LeagueCode leagueCode,
            LocalDate snapshotDate
    );

    long countByDataRefreshLog_IdAndScrapeStatus(UUID refreshLogId, ScrapeStatus scrapeStatus);

    List<RawSnapshot> findByLeague_CodeAndSnapshotDateAndScrapeStatusOrderByCreatedAtAsc(
            LeagueCode leagueCode,
            LocalDate snapshotDate,
            ScrapeStatus scrapeStatus
    );

    @Query("""
            select rs
            from RawSnapshot rs
            join fetch rs.sourceTarget st
            join fetch rs.league l
            where l.code = :leagueCode
              and rs.snapshotDate = :snapshotDate
              and rs.scrapeStatus = :scrapeStatus
              and st.sourceType = :sourceType
            order by rs.createdAt asc
            """)
    List<RawSnapshot> findByLeagueCodeDateStatusAndSourceType(
            @Param("leagueCode") LeagueCode leagueCode,
            @Param("snapshotDate") LocalDate snapshotDate,
            @Param("scrapeStatus") ScrapeStatus scrapeStatus,
            @Param("sourceType") SourceType sourceType
    );
}
