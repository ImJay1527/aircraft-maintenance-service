package pt.isep.sidis.aircraft.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public class CreateMaintenanceTemplateDTO {

    @NotBlank(message = "Template name is mandatory.")
    private String templateName;

    @NotBlank(message = "Template type is mandatory.")
    private String templateType;

    @NotNull(message = "Default duration is mandatory.")
    @Positive(message = "Default duration must be strictly positive.")
    private Double defaultDurationHours;

    @NotEmpty(message = "At least one applicable aircraft model must be specified.")
    private List<Long> applicableModelIds;

    @NotEmpty(message = "At least one checklist item is required.")
    private List<@NotBlank(message = "Checklist item description cannot be blank.") String> checklistItems;

    public CreateMaintenanceTemplateDTO() {
    }

    public CreateMaintenanceTemplateDTO(String templateName,
                                        String templateType,
                                        Double defaultDurationHours,
                                        List<Long> applicableModelIds,
                                        List<String> checklistItems) {
        this.templateName = templateName;
        this.templateType = templateType;
        this.defaultDurationHours = defaultDurationHours;
        this.applicableModelIds = applicableModelIds;
        this.checklistItems = checklistItems;
    }

    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }
    public String getTemplateType() { return templateType; }
    public void setTemplateType(String templateType) { this.templateType = templateType; }
    public Double getDefaultDurationHours() { return defaultDurationHours; }
    public void setDefaultDurationHours(Double defaultDurationHours) { this.defaultDurationHours = defaultDurationHours; }
    public List<Long> getApplicableModelIds() { return applicableModelIds; }
    public void setApplicableModelIds(List<Long> applicableModelIds) { this.applicableModelIds = applicableModelIds; }
    public List<String> getChecklistItems() { return checklistItems; }
    public void setChecklistItems(List<String> checklistItems) { this.checklistItems = checklistItems; }
}
