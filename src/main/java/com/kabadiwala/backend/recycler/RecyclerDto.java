package com.kabadiwala.backend.recycler;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RecyclerDto(
        UUID id,
        String facilityName,
        String locationAddress,
        String city,
        String contactPhone,
        String contactEmail,
        String status,
        List<String> acceptedCategoryCodes,
        Instant createdAt
) {
    public static RecyclerDto fromEntity(Recycler entity) {
        if (entity == null) return null;
        return new RecyclerDto(
                entity.getId(),
                entity.getFacilityName(),
                entity.getLocationAddress(),
                entity.getCity(),
                entity.getContactPhone(),
                entity.getContactEmail(),
                entity.getStatus(),
                entity.getAcceptedCategoryList(),
                entity.getCreatedAt()
        );
    }
}
