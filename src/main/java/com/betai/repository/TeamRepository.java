package com.betai.repository;

import com.betai.domain.league.LeagueCode;
import com.betai.domain.team.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamRepository extends JpaRepository<Team, UUID> {

    List<Team> findByLeague_CodeAndActiveTrueOrderByCanonicalNameAsc(LeagueCode leagueCode);

    List<Team> findAllByLeague_CodeAndCanonicalNameIgnoreCase(LeagueCode leagueCode, String canonicalName);

    default Optional<Team> findByLeague_CodeAndCanonicalNameIgnoreCaseSafely(LeagueCode leagueCode, String canonicalName) {
        List<Team> results = findAllByLeague_CodeAndCanonicalNameIgnoreCase(leagueCode, canonicalName);
        if (results.size() > 1) {
            throw new com.betai.exception.DuplicateEntityException(
                    "Team", null, "TEAM", leagueCode.name(), null, results.size(),
                    "Delete or merge duplicate team records with the same case-insensitive canonical name for this league."
            );
        }
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    Optional<Team> findByExternalKey(String externalKey);

    long countByLeague_Code(LeagueCode leagueCode);
}
