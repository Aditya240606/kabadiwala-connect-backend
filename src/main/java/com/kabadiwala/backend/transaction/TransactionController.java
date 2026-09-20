package com.kabadiwala.backend.transaction;

import com.kabadiwala.backend.auth.SecurityUtils;
import com.kabadiwala.backend.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@Tag(name = "Handover Transactions", description = "Endpoints for managing lot handovers between collectors and recycling facilities")
public class TransactionController {

    private final HandoverService handoverService;

    public TransactionController(HandoverService handoverService) {
        this.handoverService = handoverService;
    }

    @PostMapping({"", "/initiate"})
    @Operation(summary = "Initiate lot handover", description = "Collector assigns a lot in READY_FOR_HANDOVER state to a designated recycler")
    public ResponseEntity<ApiResponse<HandoverTransactionDto>> initiateHandover(
            @RequestBody @Valid InitiateHandoverRequest request
    ) {
        UUID collectorId = SecurityUtils.getCurrentUserId();
        HandoverTransactionDto transaction = handoverService.initiateHandover(collectorId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created("Handover initiated", transaction));
    }

    @PostMapping("/{id}/accept")
    @Operation(summary = "Accept handover", description = "Recycler confirms receipt of material lot, agrees on final weight and price per kg, and completes transaction")
    public ResponseEntity<ApiResponse<HandoverTransactionDto>> acceptHandover(
            @PathVariable UUID id,
            @RequestBody @Valid AcceptHandoverRequest request
    ) {
        HandoverTransactionDto transaction = handoverService.acceptHandover(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Handover accepted and completed", transaction));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject handover", description = "Recycler rejects handover, returning the material lot to READY_FOR_HANDOVER state")
    public ResponseEntity<ApiResponse<HandoverTransactionDto>> rejectHandover(
            @PathVariable UUID id,
            @RequestParam(required = false) String reason
    ) {
        HandoverTransactionDto transaction = handoverService.rejectHandover(id, reason);
        return ResponseEntity.ok(ApiResponse.ok("Handover rejected", transaction));
    }

    @GetMapping
    @Operation(summary = "List transactions", description = "Returns a paginated list of handover transactions for the authenticated collector")
    public ResponseEntity<ApiResponse<Page<HandoverTransactionDto>>> listTransactions(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        Page<HandoverTransactionDto> transactions = handoverService.getCollectorTransactions(currentUserId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(transactions));
    }
}
