package com.kabadiwala.backend.recycler;

import com.kabadiwala.backend.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/recyclers")
@Tag(name = "Recyclers", description = "Endpoints for finding and matching verified recycling facilities")
public class RecyclerController {

    private final RecyclerService recyclerService;

    public RecyclerController(RecyclerService recyclerService) {
        this.recyclerService = recyclerService;
    }

    @GetMapping({"/match", "/matching"})
    @Operation(summary = "Find matching recyclers", description = "Finds active recyclers that accept a given material category in an optional city")
    public ResponseEntity<ApiResponse<List<RecyclerDto>>> findMatchingRecyclers(
            @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) String city
    ) {
        List<RecyclerDto> recyclers = recyclerService.findMatchingRecyclers(categoryCode, city);
        return ResponseEntity.ok(ApiResponse.ok(recyclers));
    }
}
