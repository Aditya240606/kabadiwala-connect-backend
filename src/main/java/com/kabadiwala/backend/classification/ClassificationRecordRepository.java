package com.kabadiwala.backend.classification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClassificationRecordRepository extends JpaRepository<ClassificationRecord, UUID> {
    Optional<ClassificationRecord> findTopByLotIdOrderByCreatedAtDesc(UUID lotId);
}
