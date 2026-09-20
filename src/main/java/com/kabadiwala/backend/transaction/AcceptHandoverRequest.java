package com.kabadiwala.backend.transaction;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record AcceptHandoverRequest(
        @NotNull(message = "Confirmed weight is required")
        @DecimalMin(value = "0.001", message = "Weight must be at least 0.001 kg")
        @DecimalMax(value = "50000.0", message = "Weight exceeds maximum limit")
        BigDecimal confirmedWeightKg,

        @NotNull(message = "Confirmed rate per kg is required")
        @DecimalMin(value = "0.0", message = "Rate per kg cannot be negative")
        BigDecimal confirmedPricePerKg,

        @Size(max = 500, message = "Receipt notes must not exceed 500 characters")
        String notes
) {}
