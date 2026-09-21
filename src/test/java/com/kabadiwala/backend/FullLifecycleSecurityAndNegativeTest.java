package com.kabadiwala.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kabadiwala.backend.classification.ClassificationRecord;
import com.kabadiwala.backend.classification.ClassificationRecordRepository;
import com.kabadiwala.backend.classification.ConfirmClassificationRequest;
import com.kabadiwala.backend.material.*;
import com.kabadiwala.backend.pricing.PricingRate;
import com.kabadiwala.backend.pricing.PricingRateRepository;
import com.kabadiwala.backend.recycler.Recycler;
import com.kabadiwala.backend.recycler.RecyclerRepository;
import com.kabadiwala.backend.transaction.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class FullLifecycleSecurityAndNegativeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WasteCategoryRepository wasteCategoryRepository;

    @Autowired
    private PricingRateRepository pricingRateRepository;

    @Autowired
    private RecyclerRepository recyclerRepository;

    @Autowired
    private MaterialLotRepository materialLotRepository;

    @Autowired
    private ClassificationRecordRepository classificationRecordRepository;

    @Autowired
    private HandoverTransactionRepository transactionRepository;

    private final UUID collectorIdA = UUID.randomUUID();
    private final UUID collectorIdB = UUID.randomUUID();
    private final UUID recyclerUserIdA = UUID.randomUUID();
    private final UUID recyclerUserIdB = UUID.randomUUID();

    private UUID recyclerIdA;
    private UUID recyclerIdB;

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor collectorJwtA() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_COLLECTOR"))
                .jwt(j -> j.subject(collectorIdA.toString())
                        .claim("user_metadata", Map.of("role", "COLLECTOR")));
    }

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor collectorJwtB() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_COLLECTOR"))
                .jwt(j -> j.subject(collectorIdB.toString())
                        .claim("user_metadata", Map.of("role", "COLLECTOR")));
    }

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor recyclerJwtA() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_RECYCLER"))
                .jwt(j -> j.subject(recyclerUserIdA.toString())
                        .claim("user_metadata", Map.of("role", "RECYCLER")));
    }

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor recyclerJwtB() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_RECYCLER"))
                .jwt(j -> j.subject(recyclerUserIdB.toString())
                        .claim("user_metadata", Map.of("role", "RECYCLER")));
    }

    @BeforeEach
    void setupTestData() {
        // Seed canonical categories
        saveCategoryIfNotExists("PCB", "PCB / Circuit Board");
        saveCategoryIfNotExists("BATTERY", "Battery");
        saveCategoryIfNotExists("STORAGE_DEVICE", "Storage Device");
        saveCategoryIfNotExists("DISPLAY_SCREEN", "Display / Screen");
        saveCategoryIfNotExists("OTHER_EWASTE", "Other E-Waste");
        saveCategoryIfNotExists("NON_EWASTE", "Not E-Waste");

        // Seed pricing rates
        saveRateIfNotExists("PCB", new BigDecimal("180.00"));
        saveRateIfNotExists("BATTERY", new BigDecimal("45.00"));
        saveRateIfNotExists("STORAGE_DEVICE", new BigDecimal("120.00"));
        saveRateIfNotExists("OTHER_EWASTE", new BigDecimal("25.00"));

        // Seed recyclers
        Recycler rA = new Recycler("GreenTech Hub A", "Delhi", "9811111111", List.of("PCB", "BATTERY", "STORAGE_DEVICE"));
        rA.setUserId(recyclerUserIdA);
        this.recyclerIdA = recyclerRepository.save(rA).getId();

        Recycler rB = new Recycler("Apex Recovery B", "Noida", "9822222222", List.of("PCB", "OTHER_EWASTE"));
        rB.setUserId(recyclerUserIdB);
        this.recyclerIdB = recyclerRepository.save(rB).getId();
    }

    private void saveCategoryIfNotExists(String code, String displayName) {
        if (!wasteCategoryRepository.existsById(code)) {
            wasteCategoryRepository.save(new WasteCategory(code, displayName, displayName + " desc", true));
        }
    }

    private void saveRateIfNotExists(String code, BigDecimal rate) {
        if (pricingRateRepository.findTopByCategoryCodeAndActiveTrueOrderByEffectiveFromDesc(code).isEmpty()) {
            pricingRateRepository.save(new PricingRate(code, rate));
        }
    }

    @Test
    @DisplayName("PHASE 5 & 7: Complete Collector Happy Path, Recycler Multi-step Lifecycle, and DB Verification")
    void testCompleteCollectorAndRecyclerHappyPathWithDatabaseVerification() throws Exception {
        // 1. Fetch categories (public / authenticated)
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(6))));

        // 2. Collector creates material lot
        CreateMaterialLotRequest createReq = new CreateMaterialLotRequest(null, "Motherboards batch 101");
        MvcResult createRes = mockMvc.perform(post("/api/v1/lots")
                        .with(collectorJwtA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andReturn();

        UUID lotId = UUID.fromString(objectMapper.readTree(createRes.getResponse().getContentAsString()).get("data").get("id").asText());

        // 3. Confirm classification with local ONNX inference metadata
        ConfirmClassificationRequest onnxConfirmReq = new ConfirmClassificationRequest(
                "PCB",
                "PCB",
                new BigDecimal("0.9425"),
                "ShuffleNetV2-x1.0-onnx",
                "v1.0",
                24L,
                "High grade server boards verified by on-device model"
        );
        mockMvc.perform(post("/api/v1/lots/{id}/confirm-classification", lotId)
                        .with(collectorJwtA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(onnxConfirmReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLASSIFIED"))
                .andExpect(jsonPath("$.data.confirmedCategoryCode").value("PCB"))
                .andExpect(jsonPath("$.data.classificationMethod").value("AI_CONFIRMED"))
                .andExpect(jsonPath("$.data.classificationRecord.predictedClass").value("PCB"))
                .andExpect(jsonPath("$.data.classificationRecord.confidence").value(0.9425))
                .andExpect(jsonPath("$.data.classificationRecord.modelName").value("ShuffleNetV2-x1.0-onnx"))
                .andExpect(jsonPath("$.data.classificationRecord.inferenceLatencyMs").value(24));

        // 4. Submit weight: 5.500 kg -> Rate 180 INR/kg -> Estimated 990.00 INR
        RecordWeightRequest weightReq = new RecordWeightRequest(new BigDecimal("5.500"));
        mockMvc.perform(post("/api/v1/lots/{id}/weight", lotId)
                        .with(collectorJwtA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(weightReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PRICED"))
                .andExpect(jsonPath("$.data.weightKg").value(5.5))
                .andExpect(jsonPath("$.data.pricePerKg").value(180.00))
                .andExpect(jsonPath("$.data.estimatedPrice").value(990.00));

        // 5. Mark lot ready for handover
        mockMvc.perform(post("/api/v1/lots/{id}/ready", lotId)
                        .with(collectorJwtA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY_FOR_HANDOVER"));

        // 6. Discover matching recyclers
        mockMvc.perform(get("/api/v1/recyclers/match")
                        .param("categoryCode", "PCB")
                        .param("city", "Delhi")
                        .with(collectorJwtA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))));

        // 7. Initiate handover to Recycler A
        InitiateHandoverRequest initReq = new InitiateHandoverRequest(lotId, recyclerIdA, "Ready at collection warehouse");
        MvcResult initRes = mockMvc.perform(post("/api/v1/transactions/initiate")
                        .with(collectorJwtA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("INITIATED"))
                .andExpect(jsonPath("$.data.agreedWeightKg").value(5.5))
                .andExpect(jsonPath("$.data.totalAmount").value(990.00))
                .andReturn();

        UUID txId = UUID.fromString(objectMapper.readTree(initRes.getResponse().getContentAsString()).get("data").get("id").asText());

        // 8. Verify transaction appears in Collector's transactions list
        mockMvc.perform(get("/api/v1/transactions")
                        .with(collectorJwtA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == '" + txId + "')].status").value("INITIATED"));

        // 9. Recycler A fetches pending transactions -> discovers incoming request
        mockMvc.perform(get("/api/v1/transactions/recycler/pending")
                        .with(recyclerJwtA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == '" + txId + "')].status").value("INITIATED"));

        // 10. Recycler A gets transaction details
        mockMvc.perform(get("/api/v1/transactions/{id}", txId)
                        .with(recyclerJwtA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(txId.toString()))
                .andExpect(jsonPath("$.data.lotId").value(lotId.toString()))
                .andExpect(jsonPath("$.data.status").value("INITIATED"));

        // 11. Recycler A accepts transaction: INITIATED -> ACCEPTED
        mockMvc.perform(post("/api/v1/transactions/{id}/accept", txId)
                        .with(recyclerJwtA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\": \"Driver scheduled for pickup\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"));

        // 12. Recycler A records material collection at facility: ACCEPTED -> COLLECTED
        // Recycler weighs lot: 5.600 kg at 180 INR/kg = 1008.00 INR
        CollectHandoverRequest collectReq = new CollectHandoverRequest(
                new BigDecimal("5.600"),
                new BigDecimal("180.00"),
                "Weighed on calibrated scale #2"
        );
        mockMvc.perform(post("/api/v1/transactions/{id}/collect", txId)
                        .with(recyclerJwtA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(collectReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COLLECTED"))
                .andExpect(jsonPath("$.data.agreedWeightKg").value(5.6))
                .andExpect(jsonPath("$.data.totalAmount").value(1008.00));

        // 13. Complete transaction with digital payment: COLLECTED -> COMPLETED
        CompleteTransactionRequest completeReq = new CompleteTransactionRequest("DIGITAL", "UPI transfer ref #UPI998877");
        mockMvc.perform(post("/api/v1/transactions/{id}/complete", txId)
                        .with(recyclerJwtA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.paymentMethod").value("DIGITAL"))
                .andExpect(jsonPath("$.data.totalAmount").value(1008.00));

        // 14. Verify Collector history shows COMPLETED
        mockMvc.perform(get("/api/v1/transactions")
                        .with(collectorJwtA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == '" + txId + "')].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.content[?(@.id == '" + txId + "')].paymentMethod").value("DIGITAL"));

        // 15. Verify Recycler history shows COMPLETED
        mockMvc.perform(get("/api/v1/transactions/recycler/history")
                        .with(recyclerJwtA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == '" + txId + "')].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.content[?(@.id == '" + txId + "')].totalAmount").value(1008.00));

        // =========================================================================
        // PHASE 7: Direct Database State & Integrity Verification
        // =========================================================================
        MaterialLot lotInDb = materialLotRepository.findById(lotId).orElseThrow();
        assertEquals(LotStatus.COMPLETED, lotInDb.getStatus());
        assertEquals("PCB", lotInDb.getConfirmedCategoryCode());
        assertEquals(ClassificationMethod.AI_CONFIRMED, lotInDb.getClassificationMethod());
        assertEquals(new BigDecimal("5.500"), lotInDb.getWeightKg());
        assertEquals(new BigDecimal("1008.00"), lotInDb.getFinalPrice());

        ClassificationRecord recordInDb = classificationRecordRepository.findTopByLotIdOrderByCreatedAtDesc(lotId).orElseThrow();
        assertEquals("PCB", recordInDb.getPredictedClass());
        assertEquals(new BigDecimal("0.9425"), recordInDb.getConfidence());
        assertEquals("ShuffleNetV2-x1.0-onnx", recordInDb.getModelName());
        assertEquals("v1.0", recordInDb.getModelVersion());
        assertEquals(24L, recordInDb.getInferenceLatencyMs());
        assertEquals(ClassificationMethod.AI_CONFIRMED, recordInDb.getClassificationMethod());
        assertEquals("PCB", recordInDb.getWorkerConfirmedCategoryCode());
        assertNotNull(recordInDb.getCreatedAt());

        HandoverTransaction txInDb = transactionRepository.findById(txId).orElseThrow();
        assertEquals(collectorIdA, txInDb.getCollectorId());
        assertEquals(recyclerIdA, txInDb.getRecyclerId());
        assertEquals(lotId, txInDb.getLotId());
        assertEquals(HandoverStatus.COMPLETED, txInDb.getStatus());
        assertEquals(new BigDecimal("5.600"), txInDb.getAgreedWeightKg());
        assertEquals(new BigDecimal("180.00"), txInDb.getAgreedPricePerKg());
        assertEquals(new BigDecimal("1008.00"), txInDb.getTotalAmount());
        assertEquals("DIGITAL", txInDb.getPaymentMethod());
        assertNotNull(txInDb.getCompletedAt());
        assertNotNull(txInDb.getCreatedAt());

        // Verify zero duplicate transactions created for this lot
        List<HandoverTransaction> txsForLot = transactionRepository.findAll().stream()
                .filter(t -> t.getLotId().equals(lotId))
                .toList();
        assertEquals(1, txsForLot.size(), "Exactly one transaction record must exist for this lot");
    }

    @Test
    @DisplayName("PHASE 6: Classification Negative, Validation, and Provenance Tests")
    void testClassificationVariationsAndNegativeCases() throws Exception {
        // Create a lot for Collector A
        CreateMaterialLotRequest createReq = new CreateMaterialLotRequest(null, "Negative testing lot");
        MvcResult res = mockMvc.perform(post("/api/v1/lots")
                        .with(collectorJwtA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID lotId = UUID.fromString(objectMapper.readTree(res.getResponse().getContentAsString()).get("data").get("id").asText());

        // 1. Invalid category code -> 404
        ConfirmClassificationRequest invalidCategoryReq = new ConfirmClassificationRequest(
                "NON_EXISTENT_CAT_999", "PCB", new BigDecimal("0.90"), "ShuffleNetV2-x1.0", "v1.0", 20L, null
        );
        mockMvc.perform(post("/api/v1/lots/{id}/confirm-classification", lotId)
                        .with(collectorJwtA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidCategoryReq)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));

        // 2. Confidence < 0.0 -> 400
        ConfirmClassificationRequest negativeConfReq = new ConfirmClassificationRequest(
                "PCB", "PCB", new BigDecimal("-0.10"), "ShuffleNetV2-x1.0", "v1.0", 20L, null
        );
        mockMvc.perform(post("/api/v1/lots/{id}/confirm-classification", lotId)
                        .with(collectorJwtA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(negativeConfReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));

        // 3. Confidence > 1.0 -> 400
        ConfirmClassificationRequest highConfReq = new ConfirmClassificationRequest(
                "PCB", "PCB", new BigDecimal("1.25"), "ShuffleNetV2-x1.0", "v1.0", 20L, null
        );
        mockMvc.perform(post("/api/v1/lots/{id}/confirm-classification", lotId)
                        .with(collectorJwtA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(highConfReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));

        // 4. Missing required field (blank category code) -> 400
        ConfirmClassificationRequest missingCatReq = new ConfirmClassificationRequest(
                "", "PCB", new BigDecimal("0.85"), "ShuffleNetV2-x1.0", "v1.0", 20L, null
        );
        mockMvc.perform(post("/api/v1/lots/{id}/confirm-classification", lotId)
                        .with(collectorJwtA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(missingCatReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));

        // 5. Invalid lot ID -> 404
        UUID fakeLotId = UUID.randomUUID();
        ConfirmClassificationRequest validReq = new ConfirmClassificationRequest("PCB", "Manual check");
        mockMvc.perform(post("/api/v1/lots/{id}/confirm-classification", fakeLotId)
                        .with(collectorJwtA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validReq)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));

        // 6. Classification for another user's lot -> 403 Forbidden
        mockMvc.perform(post("/api/v1/lots/{id}/confirm-classification", lotId)
                        .with(collectorJwtB())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validReq)))
                .andExpect(status().isForbidden());

        // 7. Manual classification: confidence is null and method is MANUAL
        ConfirmClassificationRequest manualReq = new ConfirmClassificationRequest("BATTERY", "Manual battery sorting");
        mockMvc.perform(post("/api/v1/lots/{id}/confirm-classification", lotId)
                        .with(collectorJwtA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(manualReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.confirmedCategoryCode").value("BATTERY"))
                .andExpect(jsonPath("$.data.classificationMethod").value("MANUAL"))
                .andExpect(jsonPath("$.data.classificationRecord.confidence").doesNotExist());

        // 8. AI correction: model predicted "Flat-Panel-Monitor", worker corrected to "STORAGE_DEVICE"
        CreateMaterialLotRequest createLot2 = new CreateMaterialLotRequest(null, "Lot for AI correction test");
        MvcResult res2 = mockMvc.perform(post("/api/v1/lots")
                        .with(collectorJwtA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createLot2)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID lotId2 = UUID.fromString(objectMapper.readTree(res2.getResponse().getContentAsString()).get("data").get("id").asText());

        ConfirmClassificationRequest correctionReq = new ConfirmClassificationRequest(
                "STORAGE_DEVICE",
                "Flat-Panel-Monitor",
                new BigDecimal("0.8500"),
                "ShuffleNetV2-x1.0-onnx",
                "v1.0",
                21L,
                "Worker correction: not a monitor, this is an external hard drive enclosure"
        );
        mockMvc.perform(post("/api/v1/lots/{id}/confirm-classification", lotId2)
                        .with(collectorJwtA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(correctionReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.confirmedCategoryCode").value("STORAGE_DEVICE"))
                .andExpect(jsonPath("$.data.classificationMethod").value("AI_CORRECTED"))
                .andExpect(jsonPath("$.data.classificationRecord.predictedClass").value("Flat-Panel-Monitor"))
                .andExpect(jsonPath("$.data.classificationRecord.workerConfirmedCategoryCode").value("STORAGE_DEVICE"))
                .andExpect(jsonPath("$.data.classificationRecord.confidence").value(0.8500));
    }

    @Test
    @DisplayName("PHASE 6: Security, Isolation, and State Transition Invariants")
    void testSecurityAndStateTransitions() throws Exception {
        // Setup a ready lot and an initiated transaction between Collector A and Recycler A
        MaterialLot lot = new MaterialLot(collectorIdA);
        lot.setStatus(LotStatus.READY_FOR_HANDOVER);
        lot.setConfirmedCategoryCode("PCB");
        lot.setClassificationMethod(ClassificationMethod.AI_CONFIRMED);
        lot.setWeightKg(new BigDecimal("10.000"));
        lot.setPricePerKg(new BigDecimal("180.00"));
        lot.setEstimatedPrice(new BigDecimal("1800.00"));
        MaterialLot savedLot = materialLotRepository.save(lot);

        InitiateHandoverRequest initReq = new InitiateHandoverRequest(savedLot.getId(), recyclerIdA, "Test tx");
        MvcResult initRes = mockMvc.perform(post("/api/v1/transactions/initiate")
                        .with(collectorJwtA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initReq)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID txId = UUID.fromString(objectMapper.readTree(initRes.getResponse().getContentAsString()).get("data").get("id").asText());

        // 1. Unauthorized collector B accessing collector A's transaction -> 403 Forbidden
        mockMvc.perform(get("/api/v1/transactions/{id}", txId)
                        .with(collectorJwtB()))
                .andExpect(status().isForbidden());

        // 2. Unauthorized recycler B accessing recycler A's transaction -> 403 Forbidden
        mockMvc.perform(get("/api/v1/transactions/{id}", txId)
                        .with(recyclerJwtB()))
                .andExpect(status().isForbidden());

        // 3. Unauthorized recycler B attempting to accept recycler A's transaction -> 403 Forbidden
        mockMvc.perform(post("/api/v1/transactions/{id}/accept", txId)
                        .with(recyclerJwtB())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\": \"Illegal acceptance\"}"))
                .andExpect(status().isForbidden());

        // 4. Unauthorized collector B attempting to modify weight on collector A's lot -> 403 Forbidden
        mockMvc.perform(post("/api/v1/lots/{id}/weight", savedLot.getId())
                        .with(collectorJwtB())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weightKg\": 12.0}"))
                .andExpect(status().isForbidden());

        // 5. Invalid transition: Collect before accept (Status is INITIATED, not ACCEPTED) -> 409 Conflict
        CollectHandoverRequest collectReq = new CollectHandoverRequest(new BigDecimal("10.000"), new BigDecimal("180.00"), "Premature collection");
        mockMvc.perform(post("/api/v1/transactions/{id}/collect", txId)
                        .with(recyclerJwtA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(collectReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_STATE_TRANSITION"));

        // 6. Invalid transition: Complete before collect (Status is INITIATED) -> 409 Conflict
        CompleteTransactionRequest completeReq = new CompleteTransactionRequest("CASH", "Premature payment");
        mockMvc.perform(post("/api/v1/transactions/{id}/complete", txId)
                        .with(recyclerJwtA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completeReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_STATE_TRANSITION"));

        // 7. Legitimate acceptance: INITIATED -> ACCEPTED
        mockMvc.perform(post("/api/v1/transactions/{id}/accept", txId)
                        .with(recyclerJwtA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\": \"Accepted\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"));

        // 8. Invalid transition: Accept already accepted transaction -> 409 Conflict
        mockMvc.perform(post("/api/v1/transactions/{id}/accept", txId)
                        .with(recyclerJwtA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\": \"Duplicate acceptance\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_STATE_TRANSITION"));

        // 9. Invalid transition: Complete before collect (Status is now ACCEPTED, not COLLECTED) -> 409 Conflict
        mockMvc.perform(post("/api/v1/transactions/{id}/complete", txId)
                        .with(recyclerJwtA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completeReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_STATE_TRANSITION"));

        // 10. Legitimate collection: ACCEPTED -> COLLECTED
        mockMvc.perform(post("/api/v1/transactions/{id}/collect", txId)
                        .with(recyclerJwtA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(collectReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COLLECTED"));

        // 11. Legitimate completion: COLLECTED -> COMPLETED
        mockMvc.perform(post("/api/v1/transactions/{id}/complete", txId)
                        .with(recyclerJwtA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        // 12. Invalid transition: Duplicate completion -> 409 Conflict
        mockMvc.perform(post("/api/v1/transactions/{id}/complete", txId)
                        .with(recyclerJwtA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completeReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_STATE_TRANSITION"));

        // 13. Invalid transition: Reject after completion -> 409 Conflict
        mockMvc.perform(post("/api/v1/transactions/{id}/reject", txId)
                        .param("reason", "Cannot reject settled lot")
                        .with(recyclerJwtA()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_STATE_TRANSITION"));

        // 14. Invalid transaction ID -> 404 Not Found
        UUID fakeTxId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/transactions/{id}", fakeTxId)
                        .with(recyclerJwtA()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));
    }
}
