package com.kabadiwala.backend.material;

import com.kabadiwala.backend.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@Tag(name = "Waste Taxonomy", description = "Endpoints for inspecting worker-facing e-waste categories")
public class CategoryController {

    private final WasteCategoryRepository categoryRepository;

    public CategoryController(WasteCategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @GetMapping
    @Operation(summary = "List all active waste categories", description = "Returns the 14 worker-facing e-waste classification categories")
    public ResponseEntity<ApiResponse<List<WasteCategoryDto>>> listCategories() {
        List<WasteCategoryDto> categories = categoryRepository.findByActiveTrueOrderByDisplayNameAsc()
                .stream()
                .map(WasteCategoryDto::fromEntity)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(categories));
    }
}
