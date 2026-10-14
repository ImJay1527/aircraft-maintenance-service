package pt.isep.sidis.aircraft.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pt.isep.sidis.aircraft.api.dto.CreateMaintenanceTemplateDTO;
import pt.isep.sidis.aircraft.api.dto.MaintenanceTemplateResponseDTO;
import pt.isep.sidis.aircraft.api.dto.UpdateMaintenanceTemplateDTO;
import pt.isep.sidis.aircraft.domain.MaintenanceTemplate;
import pt.isep.sidis.aircraft.domain.TemplateType;
import pt.isep.sidis.aircraft.services.MaintenanceTemplateService;

import java.util.List;

@RestController
@RequestMapping("/api/maintenance-templates")
@Tag(name = "Maintenance Templates", description = "Endpoints for maintenance template management")
public class MaintenanceTemplateController {

    private final MaintenanceTemplateService templateService;
    private final MaintenanceTemplateModelAssembler assembler;

    public MaintenanceTemplateController(MaintenanceTemplateService templateService,
                                         MaintenanceTemplateModelAssembler assembler) {
        this.templateService = templateService;
        this.assembler = assembler;
    }

    @Operation(summary = "Create a maintenance template (US115A)",
               description = "Creates a reusable maintenance template with a checklist and applicable aircraft models. " +
                             "Requires MAINTENANCE_TECHNICIAN role.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Template created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request body"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "One or more aircraft model IDs not found"),
        @ApiResponse(responseCode = "409", description = "A template with the same name already exists")
    })
    @PreAuthorize("hasRole('MAINTENANCE_TECHNICIAN')")
    @PostMapping
    public ResponseEntity<MaintenanceTemplateResponseDTO> createTemplate(
            @Valid @RequestBody CreateMaintenanceTemplateDTO dto) {

        TemplateType type = parseTemplateType(dto.getTemplateType());

        MaintenanceTemplate created = templateService.createTemplate(
                dto.getTemplateName(),
                type,
                dto.getDefaultDurationHours(),
                dto.getApplicableModelIds(),
                dto.getChecklistItems()
        );

        return new ResponseEntity<>(assembler.toModel(created), HttpStatus.CREATED);
    }

    @Operation(summary = "View a maintenance template by id")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Template found and returned"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "No template found with the given id")
    })
    @PreAuthorize("hasAnyRole('MAINTENANCE_TECHNICIAN', 'MAINTENANCE_SUPERVISOR', 'ATCC')")
    @GetMapping("/{id}")
    public ResponseEntity<MaintenanceTemplateResponseDTO> getTemplateById(@PathVariable Long id) {
        MaintenanceTemplate template = templateService.getTemplateById(id);
        return ResponseEntity.ok(assembler.toModel(template));
    }

    @Operation(summary = "List all maintenance templates")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List returned"),
        @ApiResponse(responseCode = "400", description = "Invalid templateType value"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "Model not found")
    })
    @PreAuthorize("hasAnyRole('MAINTENANCE_TECHNICIAN', 'MAINTENANCE_SUPERVISOR', 'ATCC')")
    @GetMapping
    public ResponseEntity<List<MaintenanceTemplateResponseDTO>> getTemplates(
            @RequestParam(required = false) String templateType,
            @RequestParam(required = false) Long modelId) {

        List<MaintenanceTemplate> templates;
        if (modelId != null) {
            templates = templateService.getTemplatesForModel(modelId);
        } else if (templateType != null && !templateType.isBlank()) {
            templates = templateService.getTemplatesByType(parseTemplateType(templateType));
        } else {
            templates = templateService.getAllTemplates();
        }

        return ResponseEntity.ok(templates.stream().map(assembler::toModel).toList());
    }

    @Operation(summary = "Update a maintenance template (partial update)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Template updated and returned"),
        @ApiResponse(responseCode = "400", description = "Invalid request body"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "Template or model not found"),
        @ApiResponse(responseCode = "409", description = "Optimistic locking conflict or name duplicate")
    })
    @PreAuthorize("hasRole('MAINTENANCE_TECHNICIAN')")
    @PatchMapping("/{id}")
    public ResponseEntity<MaintenanceTemplateResponseDTO> updateTemplate(
            @PathVariable Long id,
            @Valid @RequestBody UpdateMaintenanceTemplateDTO dto) {

        TemplateType type = dto.getTemplateType() != null ? parseTemplateType(dto.getTemplateType()) : null;

        MaintenanceTemplate updated = templateService.updateTemplate(
                id,
                dto.getVersion(),
                dto.getTemplateName(),
                type,
                dto.getDefaultDurationHours(),
                dto.getApplicableModelIds(),
                dto.getChecklistItems()
        );

        return ResponseEntity.ok(assembler.toModel(updated));
    }

    private TemplateType parseTemplateType(String value) {
        try {
            return TemplateType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid template type: '" + value
                    + "'. Valid values are: INSPECTION, SCHEDULED_MAINTENANCE, OVERHAUL, MODIFICATION.");
        }
    }
}
