package com.kabadiwala.backend.material;

import com.kabadiwala.backend.auth.SecurityUtils;
import com.kabadiwala.backend.classification.ClassificationResultDto;
import com.kabadiwala.backend.classification.ClassificationService;
import com.kabadiwala.backend.classification.ConfirmClassificationRequest;
import com.kabadiwala.backend.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/material-lots", "/api/v1/lots"})
@Tag(name = "Material Lots", description = "Core endpoints for waste collectors to create, identify, weigh, and prepare e-waste lots")
public class MaterialLotController {

    private final MaterialLotService materialLotService;
    private final ClassificationService classificationService;
    private final com.kabadiwala.backend.user.UserService userService;

    public MaterialLotController(
            MaterialLotService materialLotService,
            ClassificationService classificationService,
            com.kabadiwala.backend.user.UserService userService
    ) {
        this.materialLotService = materialLotService;
        this.classificationService = classificationService;
        this.userService = userService;
    }

    @PostMapping(consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_JSON_VALUE})
    @Operation(summary = "Create a new material lot", description = "Drafts a new e-waste lot with optional photo upload and optional initial category")
    public ResponseEntity<ApiResponse<MaterialLotDto>> createLot(
            @RequestPart(value = "request", required = false) @Valid CreateMaterialLotRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) throws IOException {
        var currentUser = SecurityUtils.getRequiredCurrentUser();
        userService.getOrCreateProfile(currentUser);
        UUID collectorId = currentUser.getId();
        byte[] imageBytes = (image != null && !image.isEmpty()) ? image.getBytes() : null;
        String filename = image != null ? image.getOriginalFilename() : null;
        String contentType = image != null ? image.getContentType() : null;

        MaterialLotDto created = materialLotService.createLot(collectorId, request, imageBytes, filename, contentType);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created("Material lot created", created));
    }

    @GetMapping
    @Operation(summary = "List collector's material lots", description = "Returns a paginated list of material lots created by the authenticated collector")
    public ResponseEntity<ApiResponse<Page<MaterialLotDto>>> listLots(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        UUID collectorId = SecurityUtils.getCurrentUserId();
        Page<MaterialLotDto> lots = materialLotService.getCollectorLots(collectorId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(lots));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get material lot details", description = "Retrieves full details, classification record, and price breakdown for a lot")
    public ResponseEntity<ApiResponse<MaterialLotDto>> getLotById(@PathVariable UUID id) {
        MaterialLotDto lot = materialLotService.getLotById(id);
        return ResponseEntity.ok(ApiResponse.ok(lot));
    }

    @PostMapping(value = {"/{id}/classify", "/{id}/classify-ai"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Identify waste using AI", description = "Uploads photo to the FastAPI ML inference service and returns predicted class and suggested category")
    public ResponseEntity<ApiResponse<ClassificationResultDto>> classifyWithAi(
            @PathVariable UUID id,
            @RequestPart("image") MultipartFile image
    ) throws IOException {
        SecurityUtils.verifyOwnershipOrAdmin(materialLotService.getLotById(id).collectorId());

        byte[] bytes = image.getBytes();
        ClassificationResultDto result = classificationService.classifyWithAi(
                id,
                bytes,
                image.getOriginalFilename(),
                image.getContentType()
        );
        return ResponseEntity.ok(ApiResponse.ok("AI classification complete", result));
    }

    @PostMapping("/{id}/confirm-classification")
    @Operation(summary = "Confirm or correct classification", description = "Worker confirms the suggested category or manually chooses a different category")
    public ResponseEntity<ApiResponse<MaterialLotDto>> confirmClassification(
            @PathVariable UUID id,
            @RequestBody @Valid ConfirmClassificationRequest request
    ) {
        SecurityUtils.verifyOwnershipOrAdmin(materialLotService.getLotById(id).collectorId());

        MaterialLot updated = classificationService.confirmClassification(
                id,
                request
        );
        return ResponseEntity.ok(ApiResponse.ok("Classification confirmed", materialLotService.toDto(updated)));
    }

    @PostMapping({"/{id}/weight", "/{id}/record-weight"})
    @Operation(summary = "Record measured weight", description = "Records weight in kg and automatically calculates estimated price from active rates")
    public ResponseEntity<ApiResponse<MaterialLotDto>> recordWeight(
            @PathVariable UUID id,
            @RequestBody @Valid RecordWeightRequest request
    ) {
        MaterialLotDto updated = materialLotService.recordWeight(id, request.weightKg());
        return ResponseEntity.ok(ApiResponse.ok("Weight recorded and price calculated", updated));
    }

    @PostMapping({"/{id}/ready", "/{id}/ready-for-handover"})
    @Operation(summary = "Mark lot ready for handover", description = "Transitions lot state to READY_FOR_HANDOVER once classification and weight are verified")
    public ResponseEntity<ApiResponse<MaterialLotDto>> markReadyForHandover(@PathVariable UUID id) {
        MaterialLotDto updated = materialLotService.markReadyForHandover(id);
        return ResponseEntity.ok(ApiResponse.ok("Lot marked ready for handover", updated));
    }
}
