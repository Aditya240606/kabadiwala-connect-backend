package com.kabadiwala.backend.classification;

import com.kabadiwala.backend.material.ClassificationMethod;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ClassificationRecordDto(
        UUID id,
        UUID lotId,
        String predictedClass,
        BigDecimal confidence,
        String modelName,
        String modelVersion,
        Long inferenceLatencyMs,
        ClassificationMethod classificationMethod,
        String workerConfirmedCategoryCode,
        Instant createdAt
) {
    public static ClassificationRecordDto fromEntity(ClassificationRecord entity) {
        if (entity == null) return null;
        return new ClassificationRecordDto(
                entity.getId(),
                entity.getLotId(),
                entity.getPredictedClass(),
                entity.getConfidence(),
                entity.getModelName(),
                entity.getModelVersion(),
                entity.getInferenceLatencyMs(),
                entity.getClassificationMethod(),
                entity.getWorkerConfirmedCategoryCode(),
                entity.getCreatedAt()
        );
    }
}
