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

    @Transactional
    public HandoverTransactionDto acceptHandover(UUID transactionId, AcceptHandoverRequest request) {
        HandoverTransaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("HandoverTransaction", transactionId));

        if (tx.getStatus() != HandoverStatus.INITIATED) {
            throw new InvalidStateTransitionException("Transaction must be INITIATED to be accepted. Current: " + tx.getStatus());
        }

        BigDecimal finalWeight = request.confirmedWeightKg();
        BigDecimal finalRate = request.confirmedPricePerKg();
        BigDecimal finalAmount = finalWeight.multiply(finalRate).setScale(2, RoundingMode.HALF_UP);

        tx.setAgreedWeightKg(finalWeight);
        tx.setAgreedPricePerKg(finalRate);
        tx.setTotalAmount(finalAmount);
        tx.setStatus(HandoverStatus.COMPLETED);
        tx.setCompletedAt(Instant.now());
        if (request.notes() != null && !request.notes().isBlank()) {
            tx.setHandoverNotes(tx.getHandoverNotes() != null ? tx.getHandoverNotes() + " | " + request.notes() : request.notes());
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
    public HandoverTransactionDto rejectHandover(UUID transactionId, String reason) {
        HandoverTransaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("HandoverTransaction", transactionId));

        if (tx.getStatus() != HandoverStatus.INITIATED) {
            throw new InvalidStateTransitionException("Transaction must be INITIATED to be rejected. Current: " + tx.getStatus());
        }

        tx.setStatus(HandoverStatus.REJECTED);
        tx.setCompletedAt(Instant.now());
        if (reason != null && !reason.isBlank()) {
            tx.setHandoverNotes(tx.getHandoverNotes() != null ? tx.getHandoverNotes() + " | Rejection: " + reason : "Rejection: " + reason);
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
}
