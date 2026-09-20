package com.kabadiwala.backend.classification;

import com.kabadiwala.backend.material.*;
import com.kabadiwala.backend.ml.MlPredictionResponse;
import com.kabadiwala.backend.ml.WasteClassifierClient;
import com.kabadiwala.backend.pricing.PricingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClassificationServiceTest {

    @Mock
    private WasteClassifierClient wasteClassifierClient;

    private TaxonomyMappingService taxonomyMappingService;

    @Mock
    private ClassificationRecordRepository classificationRecordRepository;

    @Mock
    private MaterialLotRepository materialLotRepository;

    @Mock
    private WasteCategoryRepository wasteCategoryRepository;

    @Mock
    private PricingService pricingService;

    private ClassificationService classificationService;

    @BeforeEach
    void setUp() {
        taxonomyMappingService = new TaxonomyMappingService();
        classificationService = new ClassificationService(
                wasteClassifierClient,
                taxonomyMappingService,
                classificationRecordRepository,
                materialLotRepository,
                wasteCategoryRepository,
                pricingService
        );
    }

    @Test
    @DisplayName("AI classification: returns prediction and suggested worker category")
    void testClassifyWithAi() {
        UUID lotId = UUID.randomUUID();
        MaterialLot lot = new MaterialLot(UUID.randomUUID());
        lot.setId(lotId);

        when(materialLotRepository.findById(lotId)).thenReturn(Optional.of(lot));

        MlPredictionResponse mlResponse = new MlPredictionResponse(
                "PCB",
                new BigDecimal("0.9450"),
                "ewaste-v1",
                "1.0.0",
                120L,
                true
        );
        when(wasteClassifierClient.predict(any(), any(), any())).thenReturn(mlResponse);

        WasteCategory pcbCategory = new WasteCategory("PCB", "PCB / Circuit Board", "Boards", true);
        when(wasteCategoryRepository.findById("PCB")).thenReturn(Optional.of(pcbCategory));

        ClassificationResultDto result = classificationService.classifyWithAi(lotId, new byte[]{1, 2, 3}, "test.jpg", "image/jpeg");

        assertNotNull(result);
        assertEquals("PCB", result.predictedClass());
        assertEquals("PCB", result.suggestedCategoryCode());
        assertEquals("PCB / Circuit Board", result.suggestedCategoryDisplayName());
        assertEquals(new BigDecimal("0.9450"), result.confidence());

        verify(classificationRecordRepository, times(1)).save(any(ClassificationRecord.class));
    }

    @Test
    @DisplayName("Confirmation flow: Worker confirms AI suggestion -> AI_CONFIRMED")
    void testConfirmClassification_AiConfirmed() {
        UUID lotId = UUID.randomUUID();
        MaterialLot lot = new MaterialLot(UUID.randomUUID());
        lot.setId(lotId);

        when(materialLotRepository.findById(lotId)).thenReturn(Optional.of(lot));
        when(materialLotRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        WasteCategory category = new WasteCategory("PCB", "PCB / Circuit Board", "", true);
        when(wasteCategoryRepository.findById("PCB")).thenReturn(Optional.of(category));

        ClassificationRecord existingRecord = new ClassificationRecord();
        existingRecord.setPredictedClass("PCB");
        when(classificationRecordRepository.findTopByLotIdOrderByCreatedAtDesc(lotId))
                .thenReturn(Optional.of(existingRecord));

        MaterialLot updated = classificationService.confirmClassification(lotId, "PCB", "Looks like motherboard");

        assertEquals("PCB", updated.getConfirmedCategoryCode());
        assertEquals(ClassificationMethod.AI_CONFIRMED, updated.getClassificationMethod());
        assertEquals(LotStatus.CLASSIFIED, updated.getStatus());
    }

    @Test
    @DisplayName("Confirmation flow: Worker corrects AI suggestion -> AI_CORRECTED")
    void testConfirmClassification_AiCorrected() {
        UUID lotId = UUID.randomUUID();
        MaterialLot lot = new MaterialLot(UUID.randomUUID());
        lot.setId(lotId);

        when(materialLotRepository.findById(lotId)).thenReturn(Optional.of(lot));
        when(materialLotRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // AI predicted Power-Adapter, worker confirms as Charger / Adapter
        WasteCategory category = new WasteCategory("STORAGE_DEVICE", "Storage Device", "", true);
        when(wasteCategoryRepository.findById("STORAGE_DEVICE")).thenReturn(Optional.of(category));

        ClassificationRecord existingRecord = new ClassificationRecord();
        existingRecord.setPredictedClass("Power-Adapter"); // Suggested CHARGER_ADAPTER
        when(classificationRecordRepository.findTopByLotIdOrderByCreatedAtDesc(lotId))
                .thenReturn(Optional.of(existingRecord));

        MaterialLot updated = classificationService.confirmClassification(lotId, "STORAGE_DEVICE", "Actually an external hard drive");

        assertEquals("STORAGE_DEVICE", updated.getConfirmedCategoryCode());
        assertEquals(ClassificationMethod.AI_CORRECTED, updated.getClassificationMethod());
        assertEquals(LotStatus.CLASSIFIED, updated.getStatus());
    }

    @Test
    @DisplayName("Manual classification without prior AI run -> MANUAL")
    void testConfirmClassification_Manual() {
        UUID lotId = UUID.randomUUID();
        MaterialLot lot = new MaterialLot(UUID.randomUUID());
        lot.setId(lotId);

        when(materialLotRepository.findById(lotId)).thenReturn(Optional.of(lot));
        when(materialLotRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        WasteCategory category = new WasteCategory("BATTERY", "Battery", "", true);
        when(wasteCategoryRepository.findById("BATTERY")).thenReturn(Optional.of(category));

        // No prior AI record exists
        when(classificationRecordRepository.findTopByLotIdOrderByCreatedAtDesc(lotId))
                .thenReturn(Optional.empty());

        MaterialLot updated = classificationService.confirmClassification(lotId, "BATTERY", "Manual battery selection");

        assertEquals("BATTERY", updated.getConfirmedCategoryCode());
        assertEquals(ClassificationMethod.MANUAL, updated.getClassificationMethod());
        assertEquals(LotStatus.CLASSIFIED, updated.getStatus());

        ArgumentCaptor<ClassificationRecord> captor = ArgumentCaptor.forClass(ClassificationRecord.class);
        verify(classificationRecordRepository).save(captor.capture());
        assertEquals(ClassificationMethod.MANUAL, captor.getValue().getClassificationMethod());
    }
}
