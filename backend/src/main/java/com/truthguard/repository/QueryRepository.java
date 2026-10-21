package com.truthguard.repository;

import com.truthguard.model.QueryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QueryRepository extends JpaRepository<QueryEntity, UUID> {

    Optional<QueryEntity> findByCorrelationId(String correlationId);

    @Query("SELECT q FROM QueryEntity q LEFT JOIN FETCH q.stageTraces WHERE q.id = :id")
    Optional<QueryEntity> findByIdWithTraces(UUID id);

    List<QueryEntity> findAllByOrderByCreatedAtDesc();
}
