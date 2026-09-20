package com.kabadiwala.backend.classification;

import com.kabadiwala.backend.common.ResourceNotFoundException;
import com.kabadiwala.backend.material.*;
import com.kabadiwala.backend.ml.MlPredictionResponse;
import com.kabadiwala.backend.ml.WasteClassifierClient;
import com.kabadiwala.backend.pricing.PricingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class ClassificationService {

    private static final Logger logger = LoggerFactory.getLogger(ClassificationService.class);

    private final WasteClassifierClient wasteClassifierClient;
    private final TaxonomyMappingService taxonomyMappingService;
    private final ClassificationRecordRepository classificationRecordRepository;
    private final MaterialLotRepository materialLotRepository;
    private final WasteCategoryRepository wasteCategoryRepository;
    private final PricingService pricingService;

    public ClassificationService(
            WasteClassifierClient wasteClassifierClient,
            TaxonomyMappingService taxonomyMappingService,
            ClassificationRecordRepository classificationRecordRepository,
            MaterialLotRepository materialLotRepository,
            WasteCategoryRepository wasteCategoryRepository,
            PricingService pricingService
    ) {
        this.wasteClassifierClient = wasteClassifierClient;
        this.taxonomyMappingService = taxonomyMappingService;
        this.classificationRecordRepository = classificationRecordRepository;
        this.materialLotRepository = materialLotRepository;
        this.wasteCategoryRepository = wasteCategoryRepository;
        this.pricingService = pricingService;
    }

    @Transactional
    public ClassificationResultDto classifyWithAi(UUID lotId, byte[] imageBytes, String filename, String contentType) {
        MaterialLot lot = materialLotRepository.findById(lotId)
                .orElseThrow(() -> new ResourceNotFoundException("MaterialLot", lotId));

        MlPredictionResponse prediction;
        try {
            prediction = wasteClassifierClient.predict(imageBytes, filename, contentType);
        } catch (Exception ex) {
            logger.warn("ML inference failed or timed out for lot {}: {}. Falling back to manual worker selection.", lotId, ex.getMessage());
            prediction = MlPredictionResponse.fallbackManual();
        }

        String suggestedCategoryCode = taxonomyMappingService.suggestWorkerCategory(prediction.predictedClass());

        String displayName = wasteCategoryRepository.findById(suggestedCategoryCode)
                .map(WasteCategory::getDisplayName)
                .orElse(suggestedCategoryCode);

        boolean isManualFallback = prediction.predictedClass() == null || "manual-fallback".equalsIgnoreCase(prediction.modelName());

        // Record the raw AI inference for traceability
        ClassificationRecord record = new ClassificationRecord();
        record.setLotId(lot.getId());
        record.setPredictedClass(prediction.predictedClass());
        record.setConfidence(prediction.confidence());
        record.setModelName(prediction.modelName());
        record.setModelVersion(prediction.modelVersion());
        record.setInferenceLatencyMs(prediction.inferenceLatencyMs());
        record.setClassificationMethod(isManualFallback ? ClassificationMethod.MANUAL : ClassificationMethod.AI_CONFIRMED);
        record.setWorkerConfirmedCategoryCode(suggestedCategoryCode);
        classificationRecordRepository.save(record);

        logger.info("Classification processed for lot {}: predicted={}, suggested={}, manualFallback={}",
                lotId, prediction.predictedClass(), suggestedCategoryCode, isManualFallback);

        return new ClassificationResultDto(
                prediction.predictedClass(),
                prediction.confidence(),
                suggestedCategoryCode,
                displayName,
                prediction.modelName(),
                prediction.modelVersion(),
                prediction.needsConfirmation()
        );
    }

    @Transactional
    public MaterialLot confirmClassification(UUID lotId, String confirmedCategoryCode, String notes) {
        MaterialLot lot = materialLotRepository.findById(lotId)
                .orElseThrow(() -> new ResourceNotFoundException("MaterialLot", lotId));

        WasteCategory category = wasteCategoryRepository.findById(confirmedCategoryCode)
                .orElseThrow(() -> new ResourceNotFoundException("WasteCategory", confirmedCategoryCode));

        Optional<ClassificationRecord> latestRecordOpt = classificationRecordRepository.findTopByLotIdOrderByCreatedAtDesc(lotId);

        ClassificationMethod method;
        if (latestRecordOpt.isPresent()) {
            ClassificationRecord record = latestRecordOpt.get();
            String suggested = taxonomyMappingService.suggestWorkerCategory(record.getPredictedClass());
            if (confirmedCategoryCode.equalsIgnoreCase(suggested) || confirmedCategoryCode.equalsIgnoreCase(record.getPredictedClass())) {
                method = ClassificationMethod.AI_CONFIRMED;
            } else {
                method = ClassificationMethod.AI_CORRECTED;
            }
            record.setClassificationMethod(method);
            record.setWorkerConfirmedCategoryCode(category.getCode());
            classificationRecordRepository.save(record);
        } else {
            // Manual selection without AI prediction
            method = ClassificationMethod.MANUAL;
            ClassificationRecord record = new ClassificationRecord(
                    lotId,
                    null,
                    null,
                    "manual",
                    "none",
                    0L,
                    ClassificationMethod.MANUAL,
                    category.getCode()
            );
            classificationRecordRepository.save(record);
        }

        lot.setConfirmedCategoryCode(category.getCode());
        lot.setClassificationMethod(method);
        lot.setStatus(LotStatus.CLASSIFIED);
        if (notes != null && !notes.isBlank()) {
            lot.setNotes(notes);
        }

        // If weight was already provided, calculate price immediately
        if (lot.getWeightKg() != null && lot.getWeightKg().compareTo(java.math.BigDecimal.ZERO) > 0) {
            PricingService.PriceEstimate estimate = pricingService.calculateEstimatedPrice(category.getCode(), lot.getWeightKg());
            lot.setPricePerKg(estimate.pricePerKg());
            lot.setEstimatedPrice(estimate.estimatedTotal());
            lot.setStatus(LotStatus.PRICED);
        }

        return materialLotRepository.save(lot);
    }
}
