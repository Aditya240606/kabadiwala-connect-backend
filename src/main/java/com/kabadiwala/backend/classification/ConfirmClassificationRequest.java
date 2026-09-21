package com.kabadiwala.backend.classification;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ConfirmClassificationRequest(
        @NotBlank(message = "Confirmed category code is required")
        @Size(max = 50, message = "Category code must not exceed 50 characters")
        String confirmedCategoryCode,

        @JsonAlias({"prediction", "predicted_class"})
        @Size(max = 100, message = "Predicted class must not exceed 100 characters")
        String predictedClass,

        @DecimalMin(value = "0.0", message = "Confidence must be at least 0.0")
        @DecimalMax(value = "1.0", message = "Confidence cannot exceed 1.0")
        BigDecimal confidence,

        @JsonAlias({"model", "model_name"})
        @Size(max = 100, message = "Model name must not exceed 100 characters")
        String modelName,

        @JsonAlias({"version", "model_version"})
        @Size(max = 50, message = "Model version must not exceed 50 characters")
        String modelVersion,

        @JsonAlias({"latency_ms", "latencyMs", "inference_latency_ms"})
        Long inferenceLatencyMs,

        @Size(max = 500, message = "Notes must not exceed 500 characters")
        String notes
) {
    public boolean hasAiPrediction() {
        return (predictedClass != null && !predictedClass.isBlank())
                || confidence != null
                || (modelName != null && !modelName.isBlank());
    }

    public ConfirmClassificationRequest(String confirmedCategoryCode, String notes) {
        this(confirmedCategoryCode, null, null, null, null, null, notes);
    }
}
