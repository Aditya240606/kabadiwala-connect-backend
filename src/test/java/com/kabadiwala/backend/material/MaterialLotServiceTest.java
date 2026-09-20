package com.kabadiwala.backend.material;

import com.kabadiwala.backend.auth.UserPrincipal;
import com.kabadiwala.backend.auth.UserRole;
import com.kabadiwala.backend.classification.ClassificationRecordRepository;
import com.kabadiwala.backend.common.InvalidStateTransitionException;
import com.kabadiwala.backend.pricing.PricingService;
import com.kabadiwala.backend.storage.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaterialLotServiceTest {

    @Mock
    private MaterialLotRepository materialLotRepository;

    @Mock
    private WasteCategoryRepository wasteCategoryRepository;

    @Mock
    private ClassificationRecordRepository classificationRecordRepository;

    @Mock
    private PricingService pricingService;

    @Mock
    private StorageService storageService;

    private MaterialLotService materialLotService;
    private UUID collectorId;

    @BeforeEach
    void setUp() {
        materialLotService = new MaterialLotService(
                materialLotRepository,
                wasteCategoryRepository,
                classificationRecordRepository,
                pricingService,
                storageService
        );

        collectorId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(collectorId, "collector@kabadiwala.org", UserRole.COLLECTOR);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "n/a", principal.getAuthorities())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Create lot: saves new draft lot")
    void testCreateLot() {
        when(materialLotRepository.save(any(MaterialLot.class))).thenAnswer(i -> {
            MaterialLot l = i.getArgument(0);
            l.setId(UUID.randomUUID());
            return l;
        });

        CreateMaterialLotRequest request = new CreateMaterialLotRequest(null, "Test lot");
        MaterialLotDto dto = materialLotService.createLot(collectorId, request, null, null, null);

        assertNotNull(dto);
        assertEquals(collectorId, dto.collectorId());
        assertEquals(LotStatus.CREATED, dto.status());
        assertEquals("Test lot", dto.notes());
    }

    @Test
    @DisplayName("Record weight: calculates price and transitions to PRICED")
    void testRecordWeight() {
        UUID lotId = UUID.randomUUID();
        MaterialLot lot = new MaterialLot(collectorId);
        lot.setId(lotId);
        lot.setStatus(LotStatus.CLASSIFIED);
        lot.setConfirmedCategoryCode("PCB");

        when(materialLotRepository.findById(lotId)).thenReturn(Optional.of(lot));
        when(materialLotRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        BigDecimal rate = new BigDecimal("180.00");
        BigDecimal weight = new BigDecimal("3.000");
        BigDecimal expectedTotal = new BigDecimal("540.00");
        when(pricingService.calculateEstimatedPrice("PCB", weight))
                .thenReturn(new PricingService.PriceEstimate(rate, expectedTotal));

        MaterialLotDto updated = materialLotService.recordWeight(lotId, weight);

        assertEquals(weight, updated.weightKg());
        assertEquals(rate, updated.pricePerKg());
        assertEquals(expectedTotal, updated.estimatedPrice());
        assertEquals(LotStatus.PRICED, updated.status());
    }

    @Test
    @DisplayName("Record weight: throws InvalidStateTransitionException if lot is not yet classified")
    void testRecordWeight_ThrowsWhenUnclassified() {
        UUID lotId = UUID.randomUUID();
        MaterialLot lot = new MaterialLot(collectorId);
        lot.setId(lotId);
        lot.setStatus(LotStatus.CREATED); // Still unclassified

        when(materialLotRepository.findById(lotId)).thenReturn(Optional.of(lot));

        assertThrows(InvalidStateTransitionException.class, () ->
                materialLotService.recordWeight(lotId, new BigDecimal("2.0")));
    }

    @Test
    @DisplayName("Ready for handover: transitions PRICED lot to READY_FOR_HANDOVER")
    void testMarkReadyForHandover() {
        UUID lotId = UUID.randomUUID();
        MaterialLot lot = new MaterialLot(collectorId);
        lot.setId(lotId);
        lot.setStatus(LotStatus.PRICED);

        when(materialLotRepository.findById(lotId)).thenReturn(Optional.of(lot));
        when(materialLotRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        MaterialLotDto updated = materialLotService.markReadyForHandover(lotId);

        assertEquals(LotStatus.READY_FOR_HANDOVER, updated.status());
    }

    @Test
    @DisplayName("Ready for handover: throws exception if lot is not PRICED")
    void testMarkReadyForHandover_ThrowsIfNotPriced() {
        UUID lotId = UUID.randomUUID();
        MaterialLot lot = new MaterialLot(collectorId);
        lot.setId(lotId);
        lot.setStatus(LotStatus.CLASSIFIED); // Not yet weighed/priced

        when(materialLotRepository.findById(lotId)).thenReturn(Optional.of(lot));

        assertThrows(InvalidStateTransitionException.class, () ->
                materialLotService.markReadyForHandover(lotId));
    }
}
