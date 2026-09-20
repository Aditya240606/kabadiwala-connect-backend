package com.kabadiwala.backend.material;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "material_lots")
@EntityListeners(AuditingEntityListener.class)
public class MaterialLot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "collector_id", nullable = false)
    private UUID collectorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LotStatus status = LotStatus.CREATED;

    @Column(name = "confirmed_category_code", length = 50)
    private String confirmedCategoryCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "classification_method", length = 30)
    private ClassificationMethod classificationMethod;

    @Column(name = "weight_kg", precision = 8, scale = 3)
    private BigDecimal weightKg;

    @Column(name = "price_per_kg", precision = 10, scale = 2)
    private BigDecimal pricePerKg;

    @Column(name = "estimated_price", precision = 12, scale = 2)
    private BigDecimal estimatedPrice;

    @Column(name = "final_price", precision = 12, scale = 2)
    private BigDecimal finalPrice;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "image_storage_ref", length = 255)
    private String imageStorageRef;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public MaterialLot() {}

    public MaterialLot(UUID collectorId) {
        this.collectorId = collectorId;
        this.status = LotStatus.CREATED;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getCollectorId() {
        return collectorId;
    }

    public void setCollectorId(UUID collectorId) {
        this.collectorId = collectorId;
    }

    public LotStatus getStatus() {
        return status;
    }

    public void setStatus(LotStatus status) {
        this.status = status;
    }

    public String getConfirmedCategoryCode() {
        return confirmedCategoryCode;
    }

    public void setConfirmedCategoryCode(String confirmedCategoryCode) {
        this.confirmedCategoryCode = confirmedCategoryCode;
    }

    public ClassificationMethod getClassificationMethod() {
        return classificationMethod;
    }

    public void setClassificationMethod(ClassificationMethod classificationMethod) {
        this.classificationMethod = classificationMethod;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    public BigDecimal getPricePerKg() {
        return pricePerKg;
    }

    public void setPricePerKg(BigDecimal pricePerKg) {
        this.pricePerKg = pricePerKg;
    }

    public BigDecimal getEstimatedPrice() {
        return estimatedPrice;
    }

    public void setEstimatedPrice(BigDecimal estimatedPrice) {
        this.estimatedPrice = estimatedPrice;
    }

    public BigDecimal getFinalPrice() {
        return finalPrice;
    }

    public void setFinalPrice(BigDecimal finalPrice) {
        this.finalPrice = finalPrice;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getImageStorageRef() {
        return imageStorageRef;
    }

    public void setImageStorageRef(String imageStorageRef) {
        this.imageStorageRef = imageStorageRef;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
