package com.kabadiwala.backend.material;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record RecordWeightRequest(
        @NotNull(message = "Weight is required")
        @DecimalMin(value = "0.001", message = "Weight must be at least 0.001 kg (1 gram)")
        @DecimalMax(value = "50000.0", message = "Weight exceeds maximum allowable batch limit of 50,000 kg")
        BigDecimal weightKg
) {}
