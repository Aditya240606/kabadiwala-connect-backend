package com.kabadiwala.backend.pricing;

import com.kabadiwala.backend.common.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PricingServiceTest {

    @Mock
    private PricingRateRepository pricingRateRepository;

    private PricingService pricingService;

    @BeforeEach
    void setUp() {
        pricingService = new PricingService(pricingRateRepository);
    }

    @Test
    @DisplayName("Should correctly calculate estimated price: weight * pricePerKg")
    void testCalculateEstimatedPrice() {
        String category = "PCB";
        PricingRate rate = new PricingRate(category, new BigDecimal("180.00"));
        when(pricingRateRepository.findTopByCategoryCodeAndActiveTrueOrderByEffectiveFromDesc(category))
                .thenReturn(Optional.of(rate));

        BigDecimal weight = new BigDecimal("2.500");
        PricingService.PriceEstimate estimate = pricingService.calculateEstimatedPrice(category, weight);

        assertNotNull(estimate);
        assertEquals(new BigDecimal("180.00"), estimate.pricePerKg());
        // 2.5 * 180 = 450.00
        assertEquals(new BigDecimal("450.00"), estimate.estimatedTotal());
    }

    @Test
    @DisplayName("Should reject calculation when weight is zero or negative")
    void testCalculateEstimatedPriceWithZeroOrNegativeWeight() {
        assertThrows(IllegalArgumentException.class, () ->
                pricingService.calculateEstimatedPrice("PCB", BigDecimal.ZERO));

        assertThrows(IllegalArgumentException.class, () ->
                pricingService.calculateEstimatedPrice("PCB", new BigDecimal("-1.5")));
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when category has no active rate")
    void testRateNotFound() {
        when(pricingRateRepository.findTopByCategoryCodeAndActiveTrueOrderByEffectiveFromDesc("UNKNOWN"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                pricingService.calculateEstimatedPrice("UNKNOWN", new BigDecimal("1.0")));
    }
}
