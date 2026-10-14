package pt.isep.sidis.aircraft.api.dto;

import org.springframework.hateoas.RepresentationModel;
import pt.isep.sidis.aircraft.domain.AircraftModel;
import pt.isep.sidis.aircraft.domain.MaintenanceTemplate;

import java.util.List;

public class MaintenanceTemplateResponseDTO extends RepresentationModel<MaintenanceTemplateResponseDTO> {

    private Long id;
    private String templateName;
    private String templateType;
    private Double defaultDurationHours;
    private List<String> applicableModelNames;
    private List<ChecklistItemDTO> checklist;
    private Long version;

    public MaintenanceTemplateResponseDTO() {
    }

    public MaintenanceTemplateResponseDTO(MaintenanceTemplate template) {
        this.id = template.getId();
        this.templateName = template.getTemplateName();
        this.templateType = template.getTemplateType().name();
        this.defaultDurationHours = template.getDefaultDurationHours();
        this.applicableModelNames = template.getApplicableModels().stream()
                .map(AircraftModel::getName)
                .toList();
        this.checklist = template.getChecklist().stream()
                .map(ChecklistItemDTO::new)
                .toList();
        this.version = template.getVersion();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }
    public String getTemplateType() { return templateType; }
    public void setTemplateType(String templateType) { this.templateType = templateType; }
    public Double getDefaultDurationHours() { return defaultDurationHours; }
    public void setDefaultDurationHours(Double defaultDurationHours) { this.defaultDurationHours = defaultDurationHours; }
    public List<String> getApplicableModelNames() { return applicableModelNames; }
    public void setApplicableModelNames(List<String> applicableModelNames) { this.applicableModelNames = applicableModelNames; }
    public List<ChecklistItemDTO> getChecklist() { return checklist; }
    public void setChecklist(List<ChecklistItemDTO> checklist) { this.checklist = checklist; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
