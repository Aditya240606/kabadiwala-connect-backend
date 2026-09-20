package com.kabadiwala.backend.material;

import com.kabadiwala.backend.classification.ClassificationRecordDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MaterialLotDto(
        UUID id,
        UUID collectorId,
        LotStatus status,
        String confirmedCategoryCode,
        String confirmedCategoryDisplayName,
        ClassificationMethod classificationMethod,
        BigDecimal weightKg,
        BigDecimal pricePerKg,
        BigDecimal estimatedPrice,
        BigDecimal finalPrice,
        String imageUrl,
        String notes,
        ClassificationRecordDto classificationRecord,
        Instant createdAt,
        Instant updatedAt
) {
    public static MaterialLotDto of(MaterialLot lot, String categoryDisplayName, ClassificationRecordDto classificationRecord) {
        if (lot == null) return null;
        return new MaterialLotDto(
                lot.getId(),
                lot.getCollectorId(),
                lot.getStatus(),
                lot.getConfirmedCategoryCode(),
                categoryDisplayName,
                lot.getClassificationMethod(),
                lot.getWeightKg(),
                lot.getPricePerKg(),
                lot.getEstimatedPrice(),
                lot.getFinalPrice(),
                lot.getImageUrl(),
                lot.getNotes(),
                classificationRecord,
                lot.getCreatedAt(),
                lot.getUpdatedAt()
        );
    }
}
