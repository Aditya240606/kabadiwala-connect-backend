package com.kabadiwala.backend.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record HandoverTransactionDto(
        UUID id,
        UUID lotId,
        UUID collectorId,
        UUID recyclerId,
        BigDecimal agreedWeightKg,
        BigDecimal agreedPricePerKg,
        BigDecimal totalAmount,
        HandoverStatus status,
        String handoverNotes,
        Instant createdAt,
        Instant completedAt
) {
    public static HandoverTransactionDto fromEntity(HandoverTransaction entity) {
        if (entity == null) return null;
        return new HandoverTransactionDto(
                entity.getId(),
                entity.getLotId(),
                entity.getCollectorId(),
                entity.getRecyclerId(),
                entity.getAgreedWeightKg(),
                entity.getAgreedPricePerKg(),
                entity.getTotalAmount(),
                entity.getStatus(),
                entity.getHandoverNotes(),
                entity.getCreatedAt(),
                entity.getCompletedAt()
        );
    }
}
