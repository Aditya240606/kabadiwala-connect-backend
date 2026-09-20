package com.kabadiwala.backend.material;

import com.kabadiwala.backend.auth.SecurityUtils;
import com.kabadiwala.backend.classification.ClassificationRecordDto;
import com.kabadiwala.backend.classification.ClassificationRecordRepository;
import com.kabadiwala.backend.common.InvalidStateTransitionException;
import com.kabadiwala.backend.common.ResourceNotFoundException;
import com.kabadiwala.backend.pricing.PricingService;
import com.kabadiwala.backend.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class MaterialLotService {

    private static final Logger logger = LoggerFactory.getLogger(MaterialLotService.class);

    private final MaterialLotRepository materialLotRepository;
    private final WasteCategoryRepository wasteCategoryRepository;
    private final ClassificationRecordRepository classificationRecordRepository;
    private final PricingService pricingService;
    private final StorageService storageService;

    public MaterialLotService(
            MaterialLotRepository materialLotRepository,
            WasteCategoryRepository wasteCategoryRepository,
            ClassificationRecordRepository classificationRecordRepository,
            PricingService pricingService,
            StorageService storageService
    ) {
        this.materialLotRepository = materialLotRepository;
        this.wasteCategoryRepository = wasteCategoryRepository;
        this.classificationRecordRepository = classificationRecordRepository;
        this.pricingService = pricingService;
        this.storageService = storageService;
    }

    @Transactional
    public MaterialLotDto createLot(UUID collectorId, CreateMaterialLotRequest request, byte[] imageBytes, String filename, String contentType) {
        MaterialLot lot = new MaterialLot(collectorId);

        if (request != null && request.notes() != null) {
            lot.setNotes(request.notes());
        }

        if (imageBytes != null && imageBytes.length > 0) {
            String storageRef = storageService.uploadImage(imageBytes, filename, contentType);
            String publicUrl = storageService.getPublicUrl(storageRef);
            lot.setImageStorageRef(storageRef);
            lot.setImageUrl(publicUrl);
        }

        if (request != null && request.initialCategoryCode() != null && !request.initialCategoryCode().isBlank()) {
            WasteCategory category = wasteCategoryRepository.findById(request.initialCategoryCode())
                    .orElseThrow(() -> new ResourceNotFoundException("WasteCategory", request.initialCategoryCode()));
            lot.setConfirmedCategoryCode(category.getCode());
            lot.setClassificationMethod(ClassificationMethod.MANUAL);
            lot.setStatus(LotStatus.CLASSIFIED);
        }

        MaterialLot saved = materialLotRepository.save(lot);
        logger.info("Created new MaterialLot {} for collector {}", saved.getId(), collectorId);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public MaterialLotDto getLotById(UUID lotId) {
        MaterialLot lot = materialLotRepository.findById(lotId)
                .orElseThrow(() -> new ResourceNotFoundException("MaterialLot", lotId));
        SecurityUtils.verifyOwnershipOrAdmin(lot.getCollectorId());
        return toDto(lot);
    }

    @Transactional(readOnly = true)
    public Page<MaterialLotDto> getCollectorLots(UUID collectorId, Pageable pageable) {
        return materialLotRepository.findByCollectorIdOrderByCreatedAtDesc(collectorId, pageable)
                .map(this::toDto);
    }

    @Transactional
    public MaterialLotDto recordWeight(UUID lotId, BigDecimal weightKg) {
        MaterialLot lot = materialLotRepository.findById(lotId)
                .orElseThrow(() -> new ResourceNotFoundException("MaterialLot", lotId));
        SecurityUtils.verifyOwnershipOrAdmin(lot.getCollectorId());

        if (lot.getStatus() == LotStatus.CREATED) {
            throw new InvalidStateTransitionException("Lot must be classified before recording weight. Current status: " + lot.getStatus());
        }
        if (lot.getStatus() == LotStatus.HANDED_OVER || lot.getStatus() == LotStatus.COMPLETED) {
            throw new InvalidStateTransitionException("Cannot modify weight for lot with terminal status: " + lot.getStatus());
        }

        lot.setWeightKg(weightKg);

        if (lot.getConfirmedCategoryCode() != null) {
            PricingService.PriceEstimate estimate = pricingService.calculateEstimatedPrice(lot.getConfirmedCategoryCode(), weightKg);
            lot.setPricePerKg(estimate.pricePerKg());
            lot.setEstimatedPrice(estimate.estimatedTotal());
            lot.setStatus(LotStatus.PRICED);
        }

        MaterialLot updated = materialLotRepository.save(lot);
        logger.info("Recorded weight {} kg for lot {}, estimated price: {}", weightKg, lotId, lot.getEstimatedPrice());
        return toDto(updated);
    }

    @Transactional
    public MaterialLotDto markReadyForHandover(UUID lotId) {
        MaterialLot lot = materialLotRepository.findById(lotId)
                .orElseThrow(() -> new ResourceNotFoundException("MaterialLot", lotId));
        SecurityUtils.verifyOwnershipOrAdmin(lot.getCollectorId());

        if (lot.getStatus() != LotStatus.PRICED) {
            throw new InvalidStateTransitionException("Lot must be PRICED (with confirmed category and weight) before marking ready for handover. Current: " + lot.getStatus());
        }

        lot.setStatus(LotStatus.READY_FOR_HANDOVER);
        MaterialLot updated = materialLotRepository.save(lot);
        logger.info("Lot {} is now READY_FOR_HANDOVER", lotId);
        return toDto(updated);
    }

    public MaterialLotDto toDto(MaterialLot lot) {
        String displayName = null;
        if (lot.getConfirmedCategoryCode() != null) {
            displayName = wasteCategoryRepository.findById(lot.getConfirmedCategoryCode())
                    .map(WasteCategory::getDisplayName)
                    .orElse(lot.getConfirmedCategoryCode());
        }

        ClassificationRecordDto classificationDto = classificationRecordRepository
                .findTopByLotIdOrderByCreatedAtDesc(lot.getId())
                .map(ClassificationRecordDto::fromEntity)
                .orElse(null);

        return MaterialLotDto.of(lot, displayName, classificationDto);
    }
}
