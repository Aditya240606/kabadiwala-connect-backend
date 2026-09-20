package com.kabadiwala.backend.transaction;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record InitiateHandoverRequest(
        @NotNull(message = "Material lot ID is required")
        UUID lotId,

        @NotNull(message = "Recycler ID is required")
        UUID recyclerId,

        @Size(max = 500, message = "Handover notes must not exceed 500 characters")
        String notes
) {}
