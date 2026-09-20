package com.kabadiwala.backend.pricing;

import com.kabadiwala.backend.common.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class PricingService {

    private final PricingRateRepository pricingRateRepository;

    public PricingService(PricingRateRepository pricingRateRepository) {
        this.pricingRateRepository = pricingRateRepository;
    }

    @Transactional(readOnly = true)
    public List<PricingRateDto> getAllActiveRates() {
        return pricingRateRepository.findByActiveTrueOrderByCategoryCodeAsc()
                .stream()
                .map(PricingRateDto::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public BigDecimal getCurrentRateForCategory(String categoryCode) {
        return pricingRateRepository.findTopByCategoryCodeAndActiveTrueOrderByEffectiveFromDesc(categoryCode)
                .map(PricingRate::getPricePerKg)
                .orElseThrow(() -> new ResourceNotFoundException("Active pricing rate not found for category: " + categoryCode));
    }

    public PriceEstimate calculateEstimatedPrice(String categoryCode, BigDecimal weightKg) {
        if (weightKg == null || weightKg.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Weight must be greater than zero");
        }
        BigDecimal rate = getCurrentRateForCategory(categoryCode);
        BigDecimal total = rate.multiply(weightKg).setScale(2, RoundingMode.HALF_UP);
        return new PriceEstimate(rate, total);
    }

    public record PriceEstimate(BigDecimal pricePerKg, BigDecimal estimatedTotal) {}
}
