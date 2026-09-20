package com.kabadiwala.backend.material;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MaterialLotRepository extends JpaRepository<MaterialLot, UUID> {
    Page<MaterialLot> findByCollectorIdOrderByCreatedAtDesc(UUID collectorId, Pageable pageable);
    Optional<MaterialLot> findByIdAndCollectorId(UUID id, UUID collectorId);
}
