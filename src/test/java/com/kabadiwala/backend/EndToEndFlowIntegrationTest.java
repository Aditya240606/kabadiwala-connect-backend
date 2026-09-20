package com.kabadiwala.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kabadiwala.backend.classification.ConfirmClassificationRequest;
import com.kabadiwala.backend.material.*;
import com.kabadiwala.backend.pricing.PricingRate;
import com.kabadiwala.backend.pricing.PricingRateRepository;
import com.kabadiwala.backend.recycler.Recycler;
import com.kabadiwala.backend.recycler.RecyclerRepository;
import com.kabadiwala.backend.transaction.InitiateHandoverRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EndToEndFlowIntegrationTest {

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

    private final UUID collectorId = UUID.randomUUID();
    private final UUID recyclerUserId = UUID.randomUUID();
    private UUID recyclerId;

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor collectorJwt() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_COLLECTOR"))
                .jwt(j -> j.subject(collectorId.toString())
                        .claim("user_metadata", Map.of("role", "COLLECTOR")));
    }

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor recyclerJwt() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_RECYCLER"))
                .jwt(j -> j.subject(recyclerUserId.toString())
                        .claim("user_metadata", Map.of("role", "RECYCLER")));
    }

    @BeforeEach
    void setupTestData() {
        // Seed test categories if not present
        if (!wasteCategoryRepository.existsById("PCB")) {
            wasteCategoryRepository.save(new WasteCategory("PCB", "PCB / Circuit Board", "Circuit boards", true));
            wasteCategoryRepository.save(new WasteCategory("BATTERY", "Battery", "Batteries", true));
            wasteCategoryRepository.save(new WasteCategory("NON_EWASTE", "Not E-Waste", "Non ewaste", true));
            wasteCategoryRepository.save(new WasteCategory("OTHER_EWASTE", "Other E-Waste", "Other", true));
        }

        // Seed test pricing rates
        if (pricingRateRepository.findTopByCategoryCodeAndActiveTrueOrderByEffectiveFromDesc("PCB").isEmpty()) {
            pricingRateRepository.save(new PricingRate("PCB", new BigDecimal("180.00")));
        }

        // Seed test recycler
        Recycler recycler = new Recycler("Green E-Waste Recyclers", "Delhi", "9876543210", List.of("PCB", "BATTERY"));
        recycler.setUserId(recyclerUserId);
        Recycler savedRecycler = recyclerRepository.save(recycler);
        this.recyclerId = savedRecycler.getId();
    }

    @Test
    @DisplayName("Public API: GET /api/v1/categories returns 200 without authentication")
    void testGetCategoriesPublic() throws Exception {
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[?(@.code == 'PCB')].displayName").value("PCB / Circuit Board"));
    }

    @Test
    @DisplayName("Security: Unauthenticated request to /api/v1/lots is rejected with 401")
    void testUnauthenticatedLotsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/lots"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("End-to-End: Full lot lifecycle flow from creation to completed handover")
    void testEndToEndLifecycleFlow() throws Exception {
        // 1. Create Material Lot via /api/v1/lots
        CreateMaterialLotRequest createReq = new CreateMaterialLotRequest(null, "E2E Test Lot of motherboards");
        String createJson = objectMapper.writeValueAsString(createReq);

        MvcResult createResult = mockMvc.perform(post("/api/v1/lots")
                        .with(collectorJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andReturn();

        String lotIdStr = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asText();
        UUID lotId = UUID.fromString(lotIdStr);

        // 2. Classify / Confirm category: PCB (₹180/kg)
        ConfirmClassificationRequest confirmReq = new ConfirmClassificationRequest("PCB", "Verified printed circuit board");
        mockMvc.perform(post("/api/v1/lots/{id}/confirm-classification", lotId)
                        .with(collectorJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLASSIFIED"))
                .andExpect(jsonPath("$.data.confirmedCategoryCode").value("PCB"));

        // 3. Record Weight: 4.5 kg -> Price = 4.5 * 180 = 810.00
        RecordWeightRequest weightReq = new RecordWeightRequest(new BigDecimal("4.500"));
        mockMvc.perform(post("/api/v1/lots/{id}/weight", lotId)
                        .with(collectorJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(weightReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PRICED"))
                .andExpect(jsonPath("$.data.weightKg").value(4.5))
                .andExpect(jsonPath("$.data.estimatedPrice").value(810.00));

        // 4. Mark Ready for Handover
        mockMvc.perform(post("/api/v1/lots/{id}/ready", lotId)
                        .with(collectorJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY_FOR_HANDOVER"));

        // 5. Match Recycler
        mockMvc.perform(get("/api/v1/recyclers/match")
                        .param("categoryCode", "PCB")
                        .param("city", "Delhi")
                        .with(collectorJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))));

        // 6. Initiate Handover to Recycler
        InitiateHandoverRequest handoverReq = new InitiateHandoverRequest(lotId, recyclerId, "Delivering to facility");
        MvcResult handoverResult = mockMvc.perform(post("/api/v1/transactions/initiate")
                        .with(collectorJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(handoverReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("INITIATED"))
                .andExpect(jsonPath("$.data.lotId").value(lotId.toString()))
                .andReturn();

        String txIdStr = objectMapper.readTree(handoverResult.getResponse().getContentAsString())
                .get("data").get("id").asText();
        UUID txId = UUID.fromString(txIdStr);

        // 7. Recycler Accepts Handover & Completes Settlement
        mockMvc.perform(post("/api/v1/transactions/{id}/accept", txId)
                        .with(recyclerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmedWeightKg\": 4.500, \"confirmedPricePerKg\": 180.00, \"notes\": \"Verified intact\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.totalAmount").value(810.00));

        // 8. Verify final lot status in database is COMPLETED
        MaterialLot finalLot = materialLotRepository.findById(lotId).orElseThrow();
        assertEquals(LotStatus.COMPLETED, finalLot.getStatus());
    }

    @Test
    @DisplayName("Error Handling: Reject invalid weight and nonexistent lot")
    void testErrorHandling() throws Exception {
        // Nonexistent lot
        UUID fakeLotId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/lots/{id}", fakeLotId)
                        .with(collectorJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));

        // Invalid weight (negative)
        CreateMaterialLotRequest createReq = new CreateMaterialLotRequest(null, "Test lot");
        MvcResult res = mockMvc.perform(post("/api/v1/lots")
                        .with(collectorJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID lotId = UUID.fromString(objectMapper.readTree(res.getResponse().getContentAsString()).get("data").get("id").asText());

        mockMvc.perform(post("/api/v1/lots/{id}/weight", lotId)
                        .with(collectorJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weightKg\": -5.0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }
}
