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
    @Operation(summary = "Accept handover request", description = "Recycler accepts request. If weight/rate are provided, completes transaction; otherwise marks ACCEPTED.")
    public ResponseEntity<ApiResponse<HandoverTransactionDto>> acceptHandover(
            @PathVariable UUID id,
            @RequestBody(required = false) @Valid AcceptHandoverRequest request
    ) {
        HandoverTransactionDto transaction = handoverService.acceptHandover(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Handover accepted", transaction));
    }

    @PostMapping("/{id}/collect")
    @Operation(summary = "Record material collection", description = "Recycler verifies weight and agreed rate per kg, transitioning to COLLECTED state.")
    public ResponseEntity<ApiResponse<HandoverTransactionDto>> collectHandover(
            @PathVariable UUID id,
            @RequestBody @Valid CollectHandoverRequest request
    ) {
        HandoverTransactionDto transaction = handoverService.collectHandover(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Material collection recorded", transaction));
    }

    @PostMapping({"/{id}/complete", "/{id}/pay"})
    @Operation(summary = "Complete transaction with payment", description = "Records payment method (CASH or DIGITAL) and completes transaction and lot.")
    public ResponseEntity<ApiResponse<HandoverTransactionDto>> completeTransaction(
            @PathVariable UUID id,
            @RequestBody(required = false) @Valid CompleteTransactionRequest request
    ) {
        HandoverTransactionDto transaction = handoverService.completeTransaction(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Transaction completed and settled", transaction));
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

    @GetMapping("/{id}")
    @Operation(summary = "Get transaction details", description = "Retrieves full transaction details by ID for authorized collector or recycler")
    public ResponseEntity<ApiResponse<HandoverTransactionDto>> getTransactionById(@PathVariable UUID id) {
        HandoverTransactionDto transaction = handoverService.getTransactionById(id);
        return ResponseEntity.ok(ApiResponse.ok(transaction));
    }

    @GetMapping("/recycler/pending")
    @Operation(summary = "List pending requests for recycler", description = "Returns incoming handover requests waiting for recycler action")
    public ResponseEntity<ApiResponse<Page<HandoverTransactionDto>>> listRecyclerPendingRequests(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        var recycler = handoverService.getRecyclerForUser(currentUserId);
        Page<HandoverTransactionDto> transactions = handoverService.getRecyclerPendingTransactions(recycler.getId(), pageable);
        return ResponseEntity.ok(ApiResponse.ok(transactions));
    }

    @GetMapping({"/recycler/history", "/recycler"})
    @Operation(summary = "List recycler transaction history", description = "Returns all handover transactions assigned to the authenticated recycler")
    public ResponseEntity<ApiResponse<Page<HandoverTransactionDto>>> listRecyclerHistory(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        var recycler = handoverService.getRecyclerForUser(currentUserId);
        Page<HandoverTransactionDto> transactions = handoverService.getRecyclerTransactions(recycler.getId(), pageable);
        return ResponseEntity.ok(ApiResponse.ok(transactions));
    }

    @GetMapping
    @Operation(summary = "List transactions", description = "Returns a paginated list of handover transactions for the authenticated user based on their role")
    public ResponseEntity<ApiResponse<Page<HandoverTransactionDto>>> listTransactions(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        var current = SecurityUtils.getRequiredCurrentUser();
        if (current.getRole() == com.kabadiwala.backend.auth.UserRole.RECYCLER) {
            var recycler = handoverService.getRecyclerForUser(current.getId());
            Page<HandoverTransactionDto> transactions = handoverService.getRecyclerTransactions(recycler.getId(), pageable);
            return ResponseEntity.ok(ApiResponse.ok(transactions));
        }
        Page<HandoverTransactionDto> transactions = handoverService.getCollectorTransactions(current.getId(), pageable);
        return ResponseEntity.ok(ApiResponse.ok(transactions));
    }
}
