package com.kabadiwala.backend.pricing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PricingRateRepository extends JpaRepository<PricingRate, UUID> {
    Optional<PricingRate> findTopByCategoryCodeAndActiveTrueOrderByEffectiveFromDesc(String categoryCode);
    List<PricingRate> findByActiveTrueOrderByCategoryCodeAsc();
}
