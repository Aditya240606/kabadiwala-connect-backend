package com.kabadiwala.backend.transaction;

import com.kabadiwala.backend.auth.SecurityUtils;
import com.kabadiwala.backend.common.InvalidStateTransitionException;
import com.kabadiwala.backend.common.ResourceNotFoundException;
import com.kabadiwala.backend.material.LotStatus;
import com.kabadiwala.backend.material.MaterialLot;
import com.kabadiwala.backend.material.MaterialLotRepository;
import com.kabadiwala.backend.recycler.Recycler;
import com.kabadiwala.backend.recycler.RecyclerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Service
public class HandoverService {

    private static final Logger logger = LoggerFactory.getLogger(HandoverService.class);

    private final HandoverTransactionRepository transactionRepository;
    private final MaterialLotRepository materialLotRepository;
    private final RecyclerRepository recyclerRepository;

    public HandoverService(
            HandoverTransactionRepository transactionRepository,
            MaterialLotRepository materialLotRepository,
            RecyclerRepository recyclerRepository
    ) {
        this.transactionRepository = transactionRepository;
        this.materialLotRepository = materialLotRepository;
        this.recyclerRepository = recyclerRepository;
    }

    @Transactional
    public HandoverTransactionDto initiateHandover(UUID collectorId, InitiateHandoverRequest request) {
        MaterialLot lot = materialLotRepository.findById(request.lotId())
                .orElseThrow(() -> new ResourceNotFoundException("MaterialLot", request.lotId()));
        SecurityUtils.verifyOwnershipOrAdmin(lot.getCollectorId());

        if (lot.getStatus() != LotStatus.READY_FOR_HANDOVER) {
            throw new InvalidStateTransitionException("Lot must be in READY_FOR_HANDOVER state. Current status: " + lot.getStatus());
        }

        Recycler recycler = recyclerRepository.findById(request.recyclerId())
                .orElseThrow(() -> new ResourceNotFoundException("Recycler", request.recyclerId()));

        if (!"ACTIVE".equalsIgnoreCase(recycler.getStatus())) {
            throw new InvalidStateTransitionException("Selected recycler is not active: " + recycler.getId());
        }

        BigDecimal weight = lot.getWeightKg();
        BigDecimal pricePerKg = lot.getPricePerKg();
        BigDecimal estimatedTotal = lot.getEstimatedPrice();

        HandoverTransaction transaction = new HandoverTransaction(
                lot.getId(),
                collectorId,
                recycler.getId(),
                weight,
                pricePerKg,
                estimatedTotal
        );
        if (request.notes() != null) {
            transaction.setHandoverNotes(request.notes());
        }

        lot.setStatus(LotStatus.HANDED_OVER);
        materialLotRepository.save(lot);

        HandoverTransaction saved = transactionRepository.save(transaction);
        logger.info("Initiated handover transaction {} for lot {} to recycler {}", saved.getId(), lot.getId(), recycler.getId());
        return HandoverTransactionDto.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public Recycler getRecyclerForUser(UUID userId) {
        return recyclerRepository.findByUserId(userId)
                .or(() -> recyclerRepository.findById(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Recycler facility not found for user: " + userId));
    }

    public void verifyTransactionAccess(HandoverTransaction tx) {
        com.kabadiwala.backend.auth.UserPrincipal current = SecurityUtils.getRequiredCurrentUser();
        if (current.getRole() == com.kabadiwala.backend.auth.UserRole.ADMIN) {
            return;
        }
        boolean isCollector = tx.getCollectorId().equals(current.getId());
        boolean isRecycler = recyclerRepository.findByUserId(current.getId())
                .map(r -> r.getId().equals(tx.getRecyclerId()))
                .orElse(current.getId().equals(tx.getRecyclerId()));

        if (!isCollector && !isRecycler) {
            throw new com.kabadiwala.backend.common.UnauthorizedResourceAccessException("You are not authorized to access this transaction");
        }
    }

    @Transactional(readOnly = true)
    public HandoverTransactionDto getTransactionById(UUID transactionId) {
        HandoverTransaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("HandoverTransaction", transactionId));
        verifyTransactionAccess(tx);
        return HandoverTransactionDto.fromEntity(tx);
    }

    @Transactional(readOnly = true)
    public Page<HandoverTransactionDto> getRecyclerPendingTransactions(UUID recyclerId, Pageable pageable) {
        return transactionRepository.findByRecyclerIdAndStatusOrderByCreatedAtDesc(recyclerId, HandoverStatus.INITIATED, pageable)
                .map(HandoverTransactionDto::fromEntity);
    }

    @Transactional(readOnly = true)
    public Page<HandoverTransactionDto> getRecyclerTransactions(UUID recyclerId, Pageable pageable) {
        return transactionRepository.findByRecyclerIdOrderByCreatedAtDesc(recyclerId, pageable)
                .map(HandoverTransactionDto::fromEntity);
    }

    @Transactional
    public HandoverTransactionDto acceptHandover(UUID transactionId, AcceptHandoverRequest request) {
        HandoverTransaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("HandoverTransaction", transactionId));

        verifyTransactionAccess(tx);

        if (tx.getStatus() != HandoverStatus.INITIATED) {
            throw new InvalidStateTransitionException("Transaction must be INITIATED to be accepted. Current status: " + tx.getStatus());
        }

        // If weight is not provided, transition to ACCEPTED
        if (request == null || request.confirmedWeightKg() == null) {
            tx.setStatus(HandoverStatus.ACCEPTED);
            if (request != null && request.notes() != null && !request.notes().isBlank()) {
                tx.setHandoverNotes(appendNotes(tx.getHandoverNotes(), request.notes()));
            }
            HandoverTransaction saved = transactionRepository.save(tx);
            logger.info("Handover transaction {} status updated to ACCEPTED", transactionId);
            return HandoverTransactionDto.fromEntity(saved);
        }

        // Otherwise: one-step acceptance and completion (backward compatible)
        BigDecimal finalWeight = request.confirmedWeightKg();
        BigDecimal finalRate = request.confirmedPricePerKg() != null ? request.confirmedPricePerKg() : tx.getAgreedPricePerKg();
        BigDecimal finalAmount = finalWeight.multiply(finalRate).setScale(2, RoundingMode.HALF_UP);

        tx.setAgreedWeightKg(finalWeight);
        tx.setAgreedPricePerKg(finalRate);
        tx.setTotalAmount(finalAmount);
        if (request.paymentMethod() != null && !request.paymentMethod().isBlank()) {
            tx.setPaymentMethod(request.paymentMethod().toUpperCase());
        } else if (tx.getPaymentMethod() == null) {
            tx.setPaymentMethod("CASH");
        }
        tx.setStatus(HandoverStatus.COMPLETED);
        tx.setCompletedAt(Instant.now());
        if (request.notes() != null && !request.notes().isBlank()) {
            tx.setHandoverNotes(appendNotes(tx.getHandoverNotes(), request.notes()));
        }

        MaterialLot lot = materialLotRepository.findById(tx.getLotId())
                .orElseThrow(() -> new ResourceNotFoundException("MaterialLot", tx.getLotId()));
        lot.setFinalPrice(finalAmount);
        lot.setStatus(LotStatus.COMPLETED);
        materialLotRepository.save(lot);

        HandoverTransaction updated = transactionRepository.save(tx);
        logger.info("Handover transaction {} successfully completed for amount INR {}", transactionId, finalAmount);
        return HandoverTransactionDto.fromEntity(updated);
    }

    @Transactional
    public HandoverTransactionDto collectHandover(UUID transactionId, CollectHandoverRequest request) {
        HandoverTransaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("HandoverTransaction", transactionId));

        verifyTransactionAccess(tx);

        if (tx.getStatus() != HandoverStatus.ACCEPTED) {
            throw new InvalidStateTransitionException("Transaction must be in ACCEPTED state before recording collection. Current status: " + tx.getStatus());
        }

        BigDecimal finalWeight = request.confirmedWeightKg();
        BigDecimal finalRate = request.confirmedPricePerKg();
        BigDecimal finalAmount = finalWeight.multiply(finalRate).setScale(2, RoundingMode.HALF_UP);

        tx.setAgreedWeightKg(finalWeight);
        tx.setAgreedPricePerKg(finalRate);
        tx.setTotalAmount(finalAmount);
        tx.setStatus(HandoverStatus.COLLECTED);
        if (request.notes() != null && !request.notes().isBlank()) {
            tx.setHandoverNotes(appendNotes(tx.getHandoverNotes(), "Collection: " + request.notes()));
        }

        HandoverTransaction updated = transactionRepository.save(tx);
        logger.info("Handover transaction {} recorded as COLLECTED, agreed amount: INR {}", transactionId, finalAmount);
        return HandoverTransactionDto.fromEntity(updated);
    }

    @Transactional
    public HandoverTransactionDto completeTransaction(UUID transactionId, CompleteTransactionRequest request) {
        HandoverTransaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("HandoverTransaction", transactionId));

        verifyTransactionAccess(tx);

        if (tx.getStatus() != HandoverStatus.COLLECTED) {
            throw new InvalidStateTransitionException("Transaction must be in COLLECTED status before completing payment. Current status: " + tx.getStatus());
        }

        String paymentMethod = (request != null && request.paymentMethod() != null && !request.paymentMethod().isBlank())
                ? request.paymentMethod().toUpperCase()
                : "CASH";

        tx.setPaymentMethod(paymentMethod);
        tx.setStatus(HandoverStatus.COMPLETED);
        tx.setCompletedAt(Instant.now());
        if (request != null && request.notes() != null && !request.notes().isBlank()) {
            tx.setHandoverNotes(appendNotes(tx.getHandoverNotes(), "Payment [" + paymentMethod + "]: " + request.notes()));
        }

        MaterialLot lot = materialLotRepository.findById(tx.getLotId())
                .orElseThrow(() -> new ResourceNotFoundException("MaterialLot", tx.getLotId()));
        lot.setFinalPrice(tx.getTotalAmount());
        lot.setStatus(LotStatus.COMPLETED);
        materialLotRepository.save(lot);

        HandoverTransaction updated = transactionRepository.save(tx);
        logger.info("Handover transaction {} successfully COMPLETED with paymentMethod={}", transactionId, paymentMethod);
        return HandoverTransactionDto.fromEntity(updated);
    }

    @Transactional
    public HandoverTransactionDto rejectHandover(UUID transactionId, String reason) {
        HandoverTransaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("HandoverTransaction", transactionId));

        verifyTransactionAccess(tx);

        if (tx.getStatus() != HandoverStatus.INITIATED && tx.getStatus() != HandoverStatus.ACCEPTED) {
            throw new InvalidStateTransitionException("Transaction must be INITIATED or ACCEPTED to be rejected. Current: " + tx.getStatus());
        }

        tx.setStatus(HandoverStatus.REJECTED);
        tx.setCompletedAt(Instant.now());
        if (reason != null && !reason.isBlank()) {
            tx.setHandoverNotes(appendNotes(tx.getHandoverNotes(), "Rejection: " + reason));
        }

        // Return lot back to READY_FOR_HANDOVER so collector can re-route
        MaterialLot lot = materialLotRepository.findById(tx.getLotId())
                .orElseThrow(() -> new ResourceNotFoundException("MaterialLot", tx.getLotId()));
        lot.setStatus(LotStatus.READY_FOR_HANDOVER);
        materialLotRepository.save(lot);

        HandoverTransaction updated = transactionRepository.save(tx);
        logger.info("Handover transaction {} was rejected", transactionId);
        return HandoverTransactionDto.fromEntity(updated);
    }

    @Transactional(readOnly = true)
    public Page<HandoverTransactionDto> getCollectorTransactions(UUID collectorId, Pageable pageable) {
        return transactionRepository.findByCollectorIdOrderByCreatedAtDesc(collectorId, pageable)
                .map(HandoverTransactionDto::fromEntity);
    }

    private String appendNotes(String existing, String addition) {
        if (existing == null || existing.isBlank()) {
            return addition;
        }
        return existing + " | " + addition;
    }
}
