package com.betai.repository;

import com.betai.domain.league.LeagueCode;
import com.betai.domain.source.SourceTarget;
import com.betai.domain.source.SourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SourceTargetRepository extends JpaRepository<SourceTarget, UUID> {

    @Query("""
            select st
            from SourceTarget st
            join fetch st.league l
            where l.code = :leagueCode
              and st.active = true
            order by st.fallbackPriority asc, st.consecutiveFailures asc, st.reliabilityScore desc, st.name asc
            """)
    List<SourceTarget> findActiveByLeagueCode(@Param("leagueCode") LeagueCode leagueCode);

    @Query("""
            select st
            from SourceTarget st
            join fetch st.league l
            where l.code in :leagueCodes
              and st.active = true
            order by l.code asc, st.sourceType asc, st.fallbackPriority asc, st.consecutiveFailures asc, st.reliabilityScore desc, st.name asc
            """)
    List<SourceTarget> findActiveByLeagueCodes(@Param("leagueCodes") Collection<LeagueCode> leagueCodes);

    List<SourceTarget> findByLeague_CodeAndSourceTypeOrderByReliabilityScoreDescNameAsc(
            LeagueCode leagueCode,
            SourceType sourceType
    );

    @Query("""
            select st
            from SourceTarget st
            join fetch st.league l
            where l.code = :leagueCode
              and st.sourceType = :sourceType
              and st.active = true
            order by st.fallbackPriority asc, st.consecutiveFailures asc, st.reliabilityScore desc, st.name asc
            """)
    List<SourceTarget> findActiveByLeagueCodeAndSourceType(
            @Param("leagueCode") LeagueCode leagueCode,
            @Param("sourceType") SourceType sourceType
    );

    Optional<SourceTarget> findByLeague_CodeAndSourceTypeAndName(
            LeagueCode leagueCode,
            SourceType sourceType,
            String name
    );

    List<SourceTarget> findByLeague_CodeOrderBySourceTypeAscNameAsc(LeagueCode leagueCode);

    long countByActiveTrue();

    long countByLeague_Code(LeagueCode leagueCode);

    long countByLeague_CodeAndActiveTrue(LeagueCode leagueCode);

    List<SourceTarget> findTop50ByActiveTrueOrderByConsecutiveFailuresDescLastFailureAtDescNameAsc();
}
