package pt.isep.sidis.aircraft.api.dto;

import org.springframework.hateoas.RepresentationModel;
import pt.isep.sidis.aircraft.domain.MaintenanceRecord;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class MaintenanceRecordResponseDTO extends RepresentationModel<MaintenanceRecordResponseDTO> {

    private Long id;
    private String registrationNumber;
    private String modelName;
    private Long templateId;
    private String templateName;
    private String description;
    private LocalDate startDate;
    private Double expectedDurationHours;
    private String status;
    private String component;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private String completionNotes;
    private Double actualDurationHours;
    private Double estimatedCost;
    private Double actualCost;
    private LocalDate nextMaintenanceDueDate;
    private Double nextMaintenanceDueHours;
    private List<ChecklistItemDTO> checklist;
    private Long version;

    public MaintenanceRecordResponseDTO() {
    }

    public MaintenanceRecordResponseDTO(MaintenanceRecord record) {
        this.id = record.getId();
        this.registrationNumber = record.getAircraft().getRegistrationNumber();
        this.modelName = record.getAircraft().getModel().getName();
        this.templateId = record.getTemplate().getId();
        this.templateName = record.getTemplate().getTemplateName();
        this.description = record.getDescription();
        this.startDate = record.getStartDate();
        this.expectedDurationHours = record.getExpectedDurationHours();
        this.status = record.getStatus().name();
        this.component = record.getComponent().name();
        this.createdAt = record.getCreatedAt();
        this.completedAt = record.getCompletedAt();
        this.completionNotes = record.getCompletionNotes();
        this.actualDurationHours = record.getActualDurationHours();
        this.estimatedCost = record.getEstimatedCost();
        this.actualCost = record.getActualCost();
        this.nextMaintenanceDueDate = record.getNextMaintenanceDueDate();
        this.nextMaintenanceDueHours = record.getNextMaintenanceDueHours();
        this.checklist = record.getChecklist().stream()
                .map(ChecklistItemDTO::new)
                .toList();
        this.version = record.getVersion();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }
    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public Double getExpectedDurationHours() { return expectedDurationHours; }
    public void setExpectedDurationHours(Double expectedDurationHours) { this.expectedDurationHours = expectedDurationHours; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getComponent() { return component; }
    public void setComponent(String component) { this.component = component; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public String getCompletionNotes() { return completionNotes; }
    public void setCompletionNotes(String completionNotes) { this.completionNotes = completionNotes; }
    public Double getActualDurationHours() { return actualDurationHours; }
    public void setActualDurationHours(Double actualDurationHours) { this.actualDurationHours = actualDurationHours; }
    public Double getEstimatedCost() { return estimatedCost; }
    public void setEstimatedCost(Double estimatedCost) { this.estimatedCost = estimatedCost; }
    public Double getActualCost() { return actualCost; }
    public void setActualCost(Double actualCost) { this.actualCost = actualCost; }
    public LocalDate getNextMaintenanceDueDate() { return nextMaintenanceDueDate; }
    public void setNextMaintenanceDueDate(LocalDate nextMaintenanceDueDate) { this.nextMaintenanceDueDate = nextMaintenanceDueDate; }
    public Double getNextMaintenanceDueHours() { return nextMaintenanceDueHours; }
    public void setNextMaintenanceDueHours(Double nextMaintenanceDueHours) { this.nextMaintenanceDueHours = nextMaintenanceDueHours; }
    public List<ChecklistItemDTO> getChecklist() { return checklist; }
    public void setChecklist(List<ChecklistItemDTO> checklist) { this.checklist = checklist; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
