package com.kabadiwala.backend.transaction;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "handover_transactions")
@EntityListeners(AuditingEntityListener.class)
public class HandoverTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "lot_id", nullable = false)
    private UUID lotId;

    @Column(name = "collector_id", nullable = false)
    private UUID collectorId;

    @Column(name = "recycler_id", nullable = false)
    private UUID recyclerId;

    @Column(name = "agreed_weight_kg", nullable = false, precision = 8, scale = 3)
    private BigDecimal agreedWeightKg;

    @Column(name = "agreed_price_per_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal agreedPricePerKg;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private HandoverStatus status = HandoverStatus.INITIATED;

    @Column(name = "handover_notes", columnDefinition = "TEXT")
    private String handoverNotes;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    public HandoverTransaction() {}

    public HandoverTransaction(UUID lotId, UUID collectorId, UUID recyclerId,
                               BigDecimal agreedWeightKg, BigDecimal agreedPricePerKg, BigDecimal totalAmount) {
        this.lotId = lotId;
        this.collectorId = collectorId;
        this.recyclerId = recyclerId;
        this.agreedWeightKg = agreedWeightKg;
        this.agreedPricePerKg = agreedPricePerKg;
        this.totalAmount = totalAmount;
        this.status = HandoverStatus.INITIATED;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getLotId() {
        return lotId;
    }

    public void setLotId(UUID lotId) {
        this.lotId = lotId;
    }

    public UUID getCollectorId() {
        return collectorId;
    }

    public void setCollectorId(UUID collectorId) {
        this.collectorId = collectorId;
    }

    public UUID getRecyclerId() {
        return recyclerId;
    }

    public void setRecyclerId(UUID recyclerId) {
        this.recyclerId = recyclerId;
    }

    public BigDecimal getAgreedWeightKg() {
        return agreedWeightKg;
    }

    public void setAgreedWeightKg(BigDecimal agreedWeightKg) {
        this.agreedWeightKg = agreedWeightKg;
    }

    public BigDecimal getAgreedPricePerKg() {
        return agreedPricePerKg;
    }

    public void setAgreedPricePerKg(BigDecimal agreedPricePerKg) {
        this.agreedPricePerKg = agreedPricePerKg;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public HandoverStatus getStatus() {
        return status;
    }

    public void setStatus(HandoverStatus status) {
        this.status = status;
    }

    public String getHandoverNotes() {
        return handoverNotes;
    }

    public void setHandoverNotes(String handoverNotes) {
        this.handoverNotes = handoverNotes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
