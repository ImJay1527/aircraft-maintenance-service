package pt.isep.sidis.aircraft.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public class CompleteMaintenanceRecordDTO {

    @NotBlank(message = "Completion notes are mandatory.")
    private String completionNotes;

    @NotNull(message = "Actual duration is mandatory.")
    @Positive(message = "Actual duration must be strictly positive.")
    private Double actualDurationHours;

    @PositiveOrZero(message = "Actual cost cannot be negative.")
    private Double actualCost;

    @NotNull(message = "The (Optimistic Locking) version is mandatory.")
    private Long version;

    public CompleteMaintenanceRecordDTO() {
    }

    public CompleteMaintenanceRecordDTO(String completionNotes, Double actualDurationHours, Double actualCost, Long version) {
        this.completionNotes = completionNotes;
        this.actualDurationHours = actualDurationHours;
        this.actualCost = actualCost;
        this.version = version;
    }

    public String getCompletionNotes() { return completionNotes; }
    public void setCompletionNotes(String completionNotes) { this.completionNotes = completionNotes; }
    public Double getActualDurationHours() { return actualDurationHours; }
    public void setActualDurationHours(Double actualDurationHours) { this.actualDurationHours = actualDurationHours; }
    public Double getActualCost() { return actualCost; }
    public void setActualCost(Double actualCost) { this.actualCost = actualCost; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
