package pt.isep.sidis.aircraft.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pt.isep.sidis.aircraft.api.dto.*;
import pt.isep.sidis.aircraft.domain.MaintenanceComponent;
import pt.isep.sidis.aircraft.domain.MaintenanceRecord;
import pt.isep.sidis.aircraft.services.MaintenanceRecordService;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/maintenance-records")
@Tag(name = "Maintenance Records", description = "Endpoints for maintenance record management")
public class MaintenanceRecordController {

    private final MaintenanceRecordService recordService;
    private final MaintenanceRecordModelAssembler assembler;

    public MaintenanceRecordController(MaintenanceRecordService recordService,
                                       MaintenanceRecordModelAssembler assembler) {
        this.recordService = recordService;
        this.assembler = assembler;
    }

    @Operation(summary = "Create a maintenance record for an aircraft (US115B)")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Record created"),
        @ApiResponse(responseCode = "400", description = "Invalid request body"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "Aircraft or template not found"),
        @ApiResponse(responseCode = "409", description = "Aircraft is already UNDER_MAINTENANCE")
    })
    @PreAuthorize("hasRole('MAINTENANCE_TECHNICIAN')")
    @PostMapping
    public ResponseEntity<MaintenanceRecordResponseDTO> createRecord(
            @Valid @RequestBody CreateMaintenanceRecordDTO dto) {

        MaintenanceRecord created = recordService.createRecord(
                dto.getRegistrationNumber(),
                dto.getTemplateId(),
                dto.getDescription(),
                dto.getStartDate(),
                dto.getExpectedDurationHours(),
                parseComponent(dto.getComponent()),
                dto.getEstimatedCost()
        );

        return new ResponseEntity<>(assembler.toModel(created), HttpStatus.CREATED);
    }

    @Operation(summary = "View a maintenance record by id")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Record found and returned"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "No record found with the given id")
    })
    @PreAuthorize("hasAnyRole('MAINTENANCE_TECHNICIAN', 'MAINTENANCE_SUPERVISOR', 'ATCC')")
    @GetMapping("/{id}")
    public ResponseEntity<MaintenanceRecordResponseDTO> getRecordById(@PathVariable Long id) {
        MaintenanceRecord record = recordService.getRecordById(id);
        return ResponseEntity.ok(assembler.toModel(record));
    }

    @Operation(summary = "View all maintenance records for a specific aircraft (US116)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Records returned"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "No aircraft found with the given registration number")
    })
    @PreAuthorize("hasAnyRole('MAINTENANCE_TECHNICIAN', 'MAINTENANCE_SUPERVISOR', 'ATCC')")
    @GetMapping("/aircraft/{registrationNumber}")
    public ResponseEntity<List<MaintenanceRecordResponseDTO>> getRecordsForAircraft(
            @PathVariable String registrationNumber) {

        List<MaintenanceRecord> records = recordService.getRecordsForAircraft(registrationNumber);
        return ResponseEntity.ok(records.stream().map(assembler::toModel).toList());
    }

    @Operation(summary = "View total maintenance hours for aircraft in the fleet (US117)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Summary list returned"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PreAuthorize("hasRole('ATCC')")
    @GetMapping("/reports/total-hours")
    public ResponseEntity<List<MaintenanceHoursSummaryDTO>> getTotalMaintenanceHours() {
        List<MaintenanceHoursSummaryDTO> result = recordService.getTotalMaintenanceHoursPerAircraft()
                .stream()
                .map(MaintenanceHoursSummaryDTO::new)
                .toList();
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Start work on a planned maintenance record (PLANNED -> IN_PROGRESS)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Record transitioned to IN_PROGRESS"),
        @ApiResponse(responseCode = "400", description = "Missing version field"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "Record not found"),
        @ApiResponse(responseCode = "409", description = "Record not in PLANNED status or version conflict")
    })
    @PreAuthorize("hasRole('MAINTENANCE_TECHNICIAN')")
    @PatchMapping("/{id}/start")
    public ResponseEntity<MaintenanceRecordResponseDTO> startRecord(
            @PathVariable Long id,
            @Valid @RequestBody VersionedActionDTO dto) {

        MaintenanceRecord updated = recordService.startRecord(id, dto.getVersion());
        return ResponseEntity.ok(assembler.toModel(updated));
    }

    @Operation(summary = "Mark a maintenance record as completed (US119 — IN_PROGRESS -> COMPLETED)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Record marked COMPLETED"),
        @ApiResponse(responseCode = "400", description = "Missing or invalid fields"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "Record not found"),
        @ApiResponse(responseCode = "409", description = "Record not in IN_PROGRESS status or version conflict")
    })
    @PreAuthorize("hasRole('MAINTENANCE_TECHNICIAN')")
    @PatchMapping("/{id}/complete")
    public ResponseEntity<MaintenanceRecordResponseDTO> completeRecord(
            @PathVariable Long id,
            @Valid @RequestBody CompleteMaintenanceRecordDTO dto) {

        MaintenanceRecord updated = recordService.completeRecord(
                id,
                dto.getVersion(),
                dto.getCompletionNotes(),
                dto.getActualDurationHours(),
                dto.getActualCost()
        );

        return ResponseEntity.ok(assembler.toModel(updated));
    }

    @Operation(summary = "Cancel a planned or in-progress maintenance record")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Record canceled"),
        @ApiResponse(responseCode = "400", description = "Missing reason or version"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "Record not found"),
        @ApiResponse(responseCode = "409", description = "Record already completed or canceled or version conflict")
    })
    @PreAuthorize("hasRole('MAINTENANCE_TECHNICIAN')")
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<MaintenanceRecordResponseDTO> cancelRecord(
            @PathVariable Long id,
            @Valid @RequestBody CancelMaintenanceRecordDTO dto) {

        MaintenanceRecord updated = recordService.cancelRecord(id, dto.getVersion(), dto.getReason());
        return ResponseEntity.ok(assembler.toModel(updated));
    }

    @Operation(summary = "Search maintenance records (US218)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Matching records returned"),
        @ApiResponse(responseCode = "400", description = "Invalid component value or date range"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PreAuthorize("hasAnyRole('MAINTENANCE_TECHNICIAN', 'MAINTENANCE_SUPERVISOR', 'ATCC')")
    @GetMapping("/search")
    public ResponseEntity<List<MaintenanceRecordResponseDTO>> searchRecords(
            @RequestParam(required = false) String registrationNumber,
            @RequestParam(required = false) String component,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        List<MaintenanceRecord> records = recordService.searchRecords(registrationNumber, component, from, to);
        return ResponseEntity.ok(records.stream().map(assembler::toModel).toList());
    }

    @Operation(summary = "View all ongoing maintenance activities across the fleet (US219)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Ongoing records returned"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PreAuthorize("hasRole('MAINTENANCE_SUPERVISOR')")
    @GetMapping("/ongoing")
    public ResponseEntity<List<MaintenanceRecordResponseDTO>> getOngoingActivities() {
        List<MaintenanceRecord> records = recordService.getOngoingActivities();
        return ResponseEntity.ok(records.stream().map(assembler::toModel).toList());
    }

    @Operation(summary = "Generate maintenance cost report per aircraft (US220)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cost summary list returned"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PreAuthorize("hasRole('ATCC')")
    @GetMapping("/reports/cost-per-aircraft")
    public ResponseEntity<List<MaintenanceCostSummaryDTO>> getCostReportPerAircraft() {
        List<MaintenanceCostSummaryDTO> result = recordService.getCostReportPerAircraft()
                .stream()
                .map(MaintenanceCostSummaryDTO::new)
                .toList();
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Generate maintenance cost report per aircraft model (US220)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cost summary list returned"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PreAuthorize("hasRole('ATCC')")
    @GetMapping("/reports/cost-per-model")
    public ResponseEntity<List<MaintenanceCostSummaryDTO>> getCostReportPerAircraftModel() {
        List<MaintenanceCostSummaryDTO> result = recordService.getCostReportPerAircraftModel()
                .stream()
                .map(MaintenanceCostSummaryDTO::new)
                .toList();
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "View average maintenance turnaround time per aircraft type (US221)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Turnaround summary list returned"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PreAuthorize("hasRole('MAINTENANCE_SUPERVISOR')")
    @GetMapping("/reports/avg-turnaround")
    public ResponseEntity<List<MaintenanceTurnaroundSummaryDTO>> getAverageTurnaround() {
        List<MaintenanceTurnaroundSummaryDTO> result = recordService.getAverageTurnaroundPerAircraftModel()
                .stream()
                .map(MaintenanceTurnaroundSummaryDTO::new)
                .toList();
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "List aircraft due for scheduled maintenance by calendar date (US222)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Due records returned"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PreAuthorize("hasRole('ATCC')")
    @GetMapping("/alerts/due-by-date")
    public ResponseEntity<List<MaintenanceRecordResponseDTO>> getAlertsDueByDate() {
        List<MaintenanceRecord> records = recordService.getAlertsDueByDate();
        return ResponseEntity.ok(records.stream().map(assembler::toModel).toList());
    }

    @Operation(summary = "List aircraft due for scheduled maintenance by flight hours (US222)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Due records returned"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PreAuthorize("hasRole('ATCC')")
    @GetMapping("/alerts/due-by-flight-hours")
    public ResponseEntity<List<MaintenanceRecordResponseDTO>> getAlertsDueByFlightHours() {
        List<MaintenanceRecord> records = recordService.getAlertsDueByFlightHours();
        return ResponseEntity.ok(records.stream().map(assembler::toModel).toList());
    }

    private MaintenanceComponent parseComponent(String value) {
        try {
            return MaintenanceComponent.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid maintenance component: '" + value
                    + "'. Valid values are: ENGINE, AIRFRAME, AVIONICS, INTERIOR, EXTERIOR.");
        }
    }
}
