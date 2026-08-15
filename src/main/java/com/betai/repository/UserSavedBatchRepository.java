package com.betai.repository;

import com.betai.domain.history.UserSavedBatch;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSavedBatchRepository extends JpaRepository<UserSavedBatch, UUID> {
    
    Optional<UserSavedBatch> findByIdAndUserId(UUID id, UUID userId);

    @EntityGraph(attributePaths = "items")
    List<UserSavedBatch> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
