package com.kabadiwala.backend.classification;

import com.kabadiwala.backend.material.ClassificationMethod;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "classification_records")
public class ClassificationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "lot_id", nullable = false)
    private UUID lotId;

    @Column(name = "predicted_class", length = 100)
    private String predictedClass;

    @Column(precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "model_version", length = 50)
    private String modelVersion;

    @Column(name = "inference_latency_ms")
    private Long inferenceLatencyMs;

    @Enumerated(EnumType.STRING)
    @Column(name = "classification_method", nullable = false, length = 30)
    private ClassificationMethod classificationMethod;

    @Column(name = "worker_confirmed_category_code", length = 50)
    private String workerConfirmedCategoryCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public ClassificationRecord() {}

    public ClassificationRecord(UUID lotId, String predictedClass, BigDecimal confidence,
                                String modelName, String modelVersion, Long inferenceLatencyMs,
                                ClassificationMethod classificationMethod, String workerConfirmedCategoryCode) {
        this.lotId = lotId;
        this.predictedClass = predictedClass;
        this.confidence = confidence;
        this.modelName = modelName;
        this.modelVersion = modelVersion;
        this.inferenceLatencyMs = inferenceLatencyMs;
        this.classificationMethod = classificationMethod;
        this.workerConfirmedCategoryCode = workerConfirmedCategoryCode;
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

    public String getPredictedClass() {
        return predictedClass;
    }

    public void setPredictedClass(String predictedClass) {
        this.predictedClass = predictedClass;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public Long getInferenceLatencyMs() {
        return inferenceLatencyMs;
    }

    public void setInferenceLatencyMs(Long inferenceLatencyMs) {
        this.inferenceLatencyMs = inferenceLatencyMs;
    }

    public ClassificationMethod getClassificationMethod() {
        return classificationMethod;
    }

    public void setClassificationMethod(ClassificationMethod classificationMethod) {
        this.classificationMethod = classificationMethod;
    }

    public String getWorkerConfirmedCategoryCode() {
        return workerConfirmedCategoryCode;
    }

    public void setWorkerConfirmedCategoryCode(String workerConfirmedCategoryCode) {
        this.workerConfirmedCategoryCode = workerConfirmedCategoryCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
