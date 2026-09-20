package com.kabadiwala.backend.classification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmClassificationRequest(
        @NotBlank(message = "Confirmed category code is required")
        @Size(max = 50, message = "Category code must not exceed 50 characters")
        String confirmedCategoryCode,

        @Size(max = 500, message = "Notes must not exceed 500 characters")
        String notes
) {}
