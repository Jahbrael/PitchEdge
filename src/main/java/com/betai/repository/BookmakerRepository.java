package com.betai.repository;

import com.betai.domain.odds.Bookmaker;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BookmakerRepository extends JpaRepository<Bookmaker, UUID> {

    Optional<Bookmaker> findByCode(String code);

    long countByActiveTrue();
}
