package com.kabadiwala.backend.transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface HandoverTransactionRepository extends JpaRepository<HandoverTransaction, UUID> {
    Page<HandoverTransaction> findByCollectorIdOrderByCreatedAtDesc(UUID collectorId, Pageable pageable);
    Page<HandoverTransaction> findByRecyclerIdOrderByCreatedAtDesc(UUID recyclerId, Pageable pageable);
    Page<HandoverTransaction> findByRecyclerIdAndStatusOrderByCreatedAtDesc(UUID recyclerId, HandoverStatus status, Pageable pageable);
    Optional<HandoverTransaction> findByLotId(UUID lotId);
}
