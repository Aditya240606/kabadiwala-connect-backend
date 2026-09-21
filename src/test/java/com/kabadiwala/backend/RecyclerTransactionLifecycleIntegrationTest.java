package com.kabadiwala.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
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
class RecyclerTransactionLifecycleIntegrationTest {

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
    private HandoverTransactionRepository transactionRepository;

    private final UUID collectorId = UUID.randomUUID();
    private final UUID recyclerUserIdA = UUID.randomUUID();
    private final UUID recyclerUserIdB = UUID.randomUUID();
    private UUID recyclerIdA;
    private UUID recyclerIdB;

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor collectorJwt() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_COLLECTOR"))
                .jwt(j -> j.subject(collectorId.toString())
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
        if (!wasteCategoryRepository.existsById("PCB")) {
            wasteCategoryRepository.save(new WasteCategory("PCB", "PCB / Circuit Board", "Boards", true));
        }
        if (!wasteCategoryRepository.existsById("BATTERY")) {
            wasteCategoryRepository.save(new WasteCategory("BATTERY", "Battery", "Batteries", true));
        }
        if (pricingRateRepository.findTopByCategoryCodeAndActiveTrueOrderByEffectiveFromDesc("PCB").isEmpty()) {
            pricingRateRepository.save(new PricingRate("PCB", new BigDecimal("180.00")));
        }

        Recycler rA = new Recycler("EcoRecycle Facility A", "Delhi", "9811111111", List.of("PCB", "BATTERY"));
        rA.setUserId(recyclerUserIdA);
        Recycler savedA = recyclerRepository.save(rA);
        this.recyclerIdA = savedA.getId();

        Recycler rB = new Recycler("PureGreen Recycler B", "Noida", "9822222222", List.of("PCB"));
        rB.setUserId(recyclerUserIdB);
        Recycler savedB = recyclerRepository.save(rB);
        this.recyclerIdB = savedB.getId();
    }

    @Test
    @DisplayName("GAP 2: Local ONNX metadata ingestion and validation via POST /confirm-classification")
    void testLocalOnnxClassificationIngestion() throws Exception {
        // 1. Create a lot
        CreateMaterialLotRequest createReq = new CreateMaterialLotRequest(null, "Motherboards for ONNX test");
        MvcResult res = mockMvc.perform(post("/api/v1/lots")
                        .with(collectorJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID lotId = UUID.fromString(objectMapper.readTree(res.getResponse().getContentAsString()).get("data").get("id").asText());

        // 2. Reject invalid confidence > 1.0
        ConfirmClassificationRequest invalidReq = new ConfirmClassificationRequest(
                "PCB", "PCB", new BigDecimal("1.50"), "ShuffleNetV2-x1.0-onnx", "v1.0", 25L, null
        );
        mockMvc.perform(post("/api/v1/lots/{id}/confirm-classification", lotId)
                        .with(collectorJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));

        // 3. Reject negative confidence < 0.0
        ConfirmClassificationRequest negativeConf = new ConfirmClassificationRequest(
                "PCB", "PCB", new BigDecimal("-0.20"), "ShuffleNetV2-x1.0-onnx", "v1.0", 25L, null
        );
        mockMvc.perform(post("/api/v1/lots/{id}/confirm-classification", lotId)
                        .with(collectorJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(negativeConf)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));

        // 4. Successfully confirm with valid local ONNX metadata
        ConfirmClassificationRequest validOnnxReq = new ConfirmClassificationRequest(
                "PCB", "PCB", new BigDecimal("0.9350"), "ShuffleNetV2-x1.0-onnx", "v1.0", 22L, "Verified on phone"
        );
        mockMvc.perform(post("/api/v1/lots/{id}/confirm-classification", lotId)
                        .with(collectorJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validOnnxReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLASSIFIED"))
                .andExpect(jsonPath("$.data.confirmedCategoryCode").value("PCB"))
                .andExpect(jsonPath("$.data.classificationMethod").value("AI_CONFIRMED"))
                .andExpect(jsonPath("$.data.classificationRecord.predictedClass").value("PCB"))
                .andExpect(jsonPath("$.data.classificationRecord.confidence").value(0.9350))
                .andExpect(jsonPath("$.data.classificationRecord.modelName").value("ShuffleNetV2-x1.0-onnx"));
    }

    @Test
    @DisplayName("GAP 1 & 3: Multi-step lifecycle: INITIATED -> ACCEPTED -> COLLECTED -> COMPLETED with payment")
    void testMultiStepLifecycleAndRecyclerQueries() throws Exception {
        // Step 1: Collector creates and prepares lot
        MaterialLot lot = new MaterialLot(collectorId);
        lot.setStatus(LotStatus.READY_FOR_HANDOVER);
        lot.setConfirmedCategoryCode("PCB");
        lot.setClassificationMethod(ClassificationMethod.AI_CONFIRMED);
        lot.setWeightKg(new BigDecimal("10.000"));
        lot.setPricePerKg(new BigDecimal("180.00"));
        lot.setEstimatedPrice(new BigDecimal("1800.00"));
        MaterialLot savedLot = materialLotRepository.save(lot);

        // Step 2: Collector initiates handover to Recycler A
        InitiateHandoverRequest initReq = new InitiateHandoverRequest(savedLot.getId(), recyclerIdA, "Pickup at Sector 62");
        MvcResult initRes = mockMvc.perform(post("/api/v1/transactions/initiate")
                        .with(collectorJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("INITIATED"))
                .andReturn();

        UUID txId = UUID.fromString(objectMapper.readTree(initRes.getResponse().getContentAsString()).get("data").get("id").asText());

        // Step 3: Recycler A queries pending requests -> sees the transaction
        mockMvc.perform(get("/api/v1/transactions/recycler/pending")
                        .with(recyclerJwtA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data.content[?(@.id == '" + txId + "')].status").value("INITIATED"));

        // Step 4: Tenant isolation: Recycler B queries pending -> cannot see Recycler A's transaction
        mockMvc.perform(get("/api/v1/transactions/recycler/pending")
                        .with(recyclerJwtB()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == '" + txId + "')]").doesNotExist());

        // Step 5: Recycler B attempts unauthorized access to GET /api/v1/transactions/{id} -> 403 Forbidden
        mockMvc.perform(get("/api/v1/transactions/{id}", txId)
                        .with(recyclerJwtB()))
                .andExpect(status().isForbidden());

        // Step 6: Recycler A queries GET /api/v1/transactions/{id} -> 200 OK
        mockMvc.perform(get("/api/v1/transactions/{id}", txId)
                        .with(recyclerJwtA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(txId.toString()))
                .andExpect(jsonPath("$.data.status").value("INITIATED"));

        // Step 7: Recycler A accepts the request (Step A: INITIATED -> ACCEPTED)
        mockMvc.perform(post("/api/v1/transactions/{id}/accept", txId)
                        .with(recyclerJwtA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\": \"Driver dispatched for collection\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"));

        // Step 8: Recycler records collection and verifies weight (Step B: ACCEPTED -> COLLECTED)
        CollectHandoverRequest collectReq = new CollectHandoverRequest(
                new BigDecimal("10.500"), new BigDecimal("180.00"), "Weighed at dock 3"
        );
        mockMvc.perform(post("/api/v1/transactions/{id}/collect", txId)
                        .with(recyclerJwtA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(collectReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COLLECTED"))
                .andExpect(jsonPath("$.data.agreedWeightKg").value(10.5))
                .andExpect(jsonPath("$.data.totalAmount").value(1890.00));

        // Step 9: Recycler records payment settlement (Step C: COLLECTED -> COMPLETED)
        CompleteTransactionRequest completeReq = new CompleteTransactionRequest("DIGITAL", "Paid via IMPS transfer");
        mockMvc.perform(post("/api/v1/transactions/{id}/complete", txId)
                        .with(recyclerJwtA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.paymentMethod").value("DIGITAL"))
                .andExpect(jsonPath("$.data.totalAmount").value(1890.00));

        // Step 10: Recycler history shows COMPLETED transaction
        mockMvc.perform(get("/api/v1/transactions/recycler/history")
                        .with(recyclerJwtA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == '" + txId + "')].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.content[?(@.id == '" + txId + "')].paymentMethod").value("DIGITAL"));

        // Step 11: Invalid transition test: Attempting to collect an already COMPLETED transaction fails with 409
        mockMvc.perform(post("/api/v1/transactions/{id}/collect", txId)
                        .with(recyclerJwtA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(collectReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_STATE_TRANSITION"));
    }
}
