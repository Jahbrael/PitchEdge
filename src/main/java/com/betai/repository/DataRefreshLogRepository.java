package com.betai.repository;

import com.betai.domain.league.LeagueCode;
import com.betai.domain.refresh.DataRefreshLog;
import com.betai.domain.refresh.RefreshStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DataRefreshLogRepository extends JpaRepository<DataRefreshLog, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select d
            from DataRefreshLog d
            join fetch d.league l
            where l.code = :leagueCode
              and d.refreshDate = :refreshDate
              and d.refreshStatus = :status
            """)
    Optional<DataRefreshLog> findByLeagueDateStatusForUpdate(
            @Param("leagueCode") LeagueCode leagueCode,
            @Param("refreshDate") LocalDate refreshDate,
            @Param("status") RefreshStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select d
            from DataRefreshLog d
            join fetch d.league l
            where l.code = :leagueCode
              and d.refreshDate = :refreshDate
            order by d.startedAt desc
            """)
    List<DataRefreshLog> findByLeagueDateForUpdate(
            @Param("leagueCode") LeagueCode leagueCode,
            @Param("refreshDate") LocalDate refreshDate
    );

    Optional<DataRefreshLog> findFirstByLeague_CodeAndRefreshDateAndRefreshStatusOrderByStartedAtDesc(
            LeagueCode leagueCode,
            LocalDate refreshDate,
            RefreshStatus status
    );

    List<DataRefreshLog> findByLeague_CodeAndRefreshDateOrderByStartedAtDesc(
            LeagueCode leagueCode,
            LocalDate refreshDate
    );

    Optional<DataRefreshLog> findFirstByLeague_CodeOrderByStartedAtDesc(LeagueCode leagueCode);

    List<DataRefreshLog> findTop10ByOrderByStartedAtDesc();
}
