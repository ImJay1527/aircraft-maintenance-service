package pt.isep.sidis.aircraft.api.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

public class CreateMaintenanceRecordDTO {

    @NotBlank(message = "Aircraft registration number is mandatory.")
    private String registrationNumber;

    @NotNull(message = "Maintenance template id is mandatory.")
    private Long templateId;

    @NotBlank(message = "Description is mandatory.")
    private String description;

    @NotNull(message = "Start date is mandatory.")
    private LocalDate startDate;

    @Positive(message = "Expected duration must be strictly positive.")
    private Double expectedDurationHours;

    @NotBlank(message = "Maintenance component is mandatory.")
    private String component;

    @PositiveOrZero(message = "Estimated cost cannot be negative.")
    private Double estimatedCost;

    public CreateMaintenanceRecordDTO() {
    }

    public CreateMaintenanceRecordDTO(String registrationNumber,
                                      Long templateId,
                                      String description,
                                      LocalDate startDate,
                                      Double expectedDurationHours,
                                      String component,
                                      Double estimatedCost) {
        this.registrationNumber = registrationNumber;
        this.templateId = templateId;
        this.description = description;
        this.startDate = startDate;
        this.expectedDurationHours = expectedDurationHours;
        this.component = component;
        this.estimatedCost = estimatedCost;
    }

    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; }
    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public Double getExpectedDurationHours() { return expectedDurationHours; }
    public void setExpectedDurationHours(Double expectedDurationHours) { this.expectedDurationHours = expectedDurationHours; }
    public String getComponent() { return component; }
    public void setComponent(String component) { this.component = component; }
    public Double getEstimatedCost() { return estimatedCost; }
    public void setEstimatedCost(Double estimatedCost) { this.estimatedCost = estimatedCost; }
}
