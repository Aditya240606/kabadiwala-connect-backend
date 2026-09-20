package com.kabadiwala.backend.pricing;

import com.kabadiwala.backend.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/pricing")
@Tag(name = "Pricing", description = "Endpoints for checking current price-per-kg rates and calculating estimates")
public class PricingController {

    private final PricingService pricingService;

    public PricingController(PricingService pricingService) {
        this.pricingService = pricingService;
    }

    @GetMapping("/rates")
    @Operation(summary = "List all active pricing rates", description = "Returns price per kg in INR for all e-waste categories")
    public ResponseEntity<ApiResponse<List<PricingRateDto>>> listActiveRates() {
        List<PricingRateDto> rates = pricingService.getAllActiveRates();
        return ResponseEntity.ok(ApiResponse.ok(rates));
    }

    @GetMapping("/estimate")
    @Operation(summary = "Calculate estimated price", description = "Computes estimated payout given category code and weight in kg")
    public ResponseEntity<ApiResponse<PricingService.PriceEstimate>> calculateEstimate(
            @RequestParam String categoryCode,
            @RequestParam BigDecimal weightKg
    ) {
        PricingService.PriceEstimate estimate = pricingService.calculateEstimatedPrice(categoryCode, weightKg);
        return ResponseEntity.ok(ApiResponse.ok(estimate));
    }
}
