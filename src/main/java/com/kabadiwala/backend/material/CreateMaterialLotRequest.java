package com.kabadiwala.backend.material;

import jakarta.validation.constraints.Size;

public record CreateMaterialLotRequest(
        @Size(max = 50, message = "Category code must not exceed 50 characters")
        String initialCategoryCode,

        @Size(max = 500, message = "Notes must not exceed 500 characters")
        String notes
) {}
