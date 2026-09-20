package com.kabadiwala.backend.ml;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record MlPredictionResponse(
        @JsonProperty("predicted_class") String predictedClass,
        @JsonProperty("confidence") BigDecimal confidence,
        @JsonProperty("model_name") String modelName,
        @JsonProperty("model_version") String modelVersion,
        @JsonAlias({"inference_time_ms", "inference_latency_ms", "latency_ms"})
        @JsonProperty("inference_latency_ms") Long inferenceLatencyMs,
        @JsonProperty("needs_confirmation") boolean needsConfirmation
) {
    public static MlPredictionResponse fallbackManual() {
        return new MlPredictionResponse(
                null,
                BigDecimal.ZERO,
                "manual-fallback",
                "none",
                0L,
                true
        );
    }
}
