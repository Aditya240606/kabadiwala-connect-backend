package com.kabadiwala.backend.material;

public record WasteCategoryDto(
        String code,
        String displayName,
        String description,
        boolean active
) {
    public static WasteCategoryDto fromEntity(WasteCategory entity) {
        if (entity == null) return null;
        return new WasteCategoryDto(
                entity.getCode(),
                entity.getDisplayName(),
                entity.getDescription(),
                entity.isActive()
        );
    }
}
