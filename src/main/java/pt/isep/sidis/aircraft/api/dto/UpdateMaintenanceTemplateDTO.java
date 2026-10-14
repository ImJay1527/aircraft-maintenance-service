package pt.isep.sidis.aircraft.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public class UpdateMaintenanceTemplateDTO {

    private String templateName;
    private String templateType;

    @Positive(message = "Default duration must be strictly positive.")
    private Double defaultDurationHours;

    private List<Long> applicableModelIds;
    private List<String> checklistItems;

    @NotNull(message = "The (Optimistic Locking) version is mandatory.")
    private Long version;

    public UpdateMaintenanceTemplateDTO() {
    }

    public UpdateMaintenanceTemplateDTO(String templateName,
                                        String templateType,
                                        Double defaultDurationHours,
                                        List<Long> applicableModelIds,
                                        List<String> checklistItems,
                                        Long version) {
        this.templateName = templateName;
        this.templateType = templateType;
        this.defaultDurationHours = defaultDurationHours;
        this.applicableModelIds = applicableModelIds;
        this.checklistItems = checklistItems;
        this.version = version;
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
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
