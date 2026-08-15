package com.betai.repository;

import com.betai.domain.league.LeagueCode;
import com.betai.domain.team.TeamAlias;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TeamAliasRepository extends JpaRepository<TeamAlias, UUID> {

    Optional<TeamAlias> findByLeague_CodeAndAliasNormalized(LeagueCode leagueCode, String aliasNormalized);
}
