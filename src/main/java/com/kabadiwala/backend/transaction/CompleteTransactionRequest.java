package com.kabadiwala.backend.transaction;

import jakarta.validation.constraints.Size;

public record CompleteTransactionRequest(
        @Size(max = 30, message = "Payment method must not exceed 30 characters")
        String paymentMethod, // e.g. CASH, DIGITAL

        @Size(max = 500, message = "Completion notes must not exceed 500 characters")
        String notes
) {}
