package com.betai.repository;

import com.betai.domain.market.MarketCode;
import com.betai.domain.market.MarketDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarketDefinitionRepository extends JpaRepository<MarketDefinition, UUID> {

    Optional<MarketDefinition> findByCode(MarketCode code);

    List<MarketDefinition> findByCodeInAndEnabledTrue(Collection<MarketCode> codes);

    List<MarketDefinition> findByEnabledTrueOrderByDisplayNameAsc();

    long countByEnabledTrue();
}
