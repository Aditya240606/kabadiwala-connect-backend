package com.kabadiwala.backend.pricing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PricingRateDto(
        UUID id,
        String categoryCode,
        BigDecimal pricePerKg,
        String currency,
        Instant effectiveFrom,
        boolean active
) {
    public static PricingRateDto fromEntity(PricingRate entity) {
        if (entity == null) return null;
        return new PricingRateDto(
                entity.getId(),
                entity.getCategoryCode(),
                entity.getPricePerKg(),
                entity.getCurrency(),
                entity.getEffectiveFrom(),
                entity.isActive()
        );
    }
}
