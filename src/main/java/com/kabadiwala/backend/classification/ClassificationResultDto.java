package com.kabadiwala.backend.classification;

import java.math.BigDecimal;

public record ClassificationResultDto(
        String predictedClass,
        BigDecimal confidence,
        String suggestedCategoryCode,
        String suggestedCategoryDisplayName,
        String modelName,
        String modelVersion,
        boolean needsConfirmation,
        boolean requiresManualSelection
) {
    public ClassificationResultDto(
            String predictedClass,
            BigDecimal confidence,
            String suggestedCategoryCode,
            String suggestedCategoryDisplayName,
            String modelName,
            String modelVersion,
            boolean needsConfirmation
    ) {
        this(predictedClass, confidence, suggestedCategoryCode, suggestedCategoryDisplayName,
                modelName, modelVersion, needsConfirmation, predictedClass == null || "manual-fallback".equalsIgnoreCase(modelName));
    }
}
