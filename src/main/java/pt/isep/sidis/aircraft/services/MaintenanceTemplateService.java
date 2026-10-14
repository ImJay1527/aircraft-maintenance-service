package pt.isep.sidis.aircraft.services;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.isep.sidis.aircraft.common.exceptions.ResourceNotFoundException;
import pt.isep.sidis.aircraft.domain.AircraftModel;
import pt.isep.sidis.aircraft.domain.MaintenanceTemplate;
import pt.isep.sidis.aircraft.domain.TemplateType;
import pt.isep.sidis.aircraft.repositories.AircraftModelRepository;
import pt.isep.sidis.aircraft.repositories.MaintenanceTemplateRepository;

import java.util.List;

/**
 * Application service for MaintenanceTemplate management (US115A).
 */
@Service
public class MaintenanceTemplateService {

    private final MaintenanceTemplateRepository templateRepository;
    private final AircraftModelRepository aircraftModelRepository;

    public MaintenanceTemplateService(MaintenanceTemplateRepository templateRepository,
                                      AircraftModelRepository aircraftModelRepository) {
        this.templateRepository = templateRepository;
        this.aircraftModelRepository = aircraftModelRepository;
    }

    @Transactional
    public MaintenanceTemplate createTemplate(String templateName,
                                              TemplateType templateType,
                                              Double defaultDurationHours,
                                              List<Long> modelIds,
                                              List<String> checklistDescriptions) {

        if (templateRepository.existsByTemplateName(templateName)) {
            throw new IllegalStateException(
                    "A maintenance template with name '" + templateName + "' already exists.");
        }

        List<AircraftModel> models = resolveModels(modelIds);

        MaintenanceTemplate template = new MaintenanceTemplate(
                templateName,
                templateType,
                defaultDurationHours,
                models,
                checklistDescriptions
        );

        return templateRepository.save(template);
    }

    @Transactional(readOnly = true)
    public List<MaintenanceTemplate> getAllTemplates() {
        return templateRepository.findAll();
    }

    @Transactional(readOnly = true)
    public MaintenanceTemplate getTemplateById(Long id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Maintenance template with id '" + id + "' not found."));
    }

    @Transactional(readOnly = true)
    public List<MaintenanceTemplate> getTemplatesByType(TemplateType type) {
        return templateRepository.findByTemplateType(type);
    }

    @Transactional(readOnly = true)
    public List<MaintenanceTemplate> getTemplatesForModel(Long modelId) {
        aircraftModelRepository.findById(modelId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aircraft model with id '" + modelId + "' not found."));

        return templateRepository.findByApplicableModel(modelId);
    }

    @Transactional
    public MaintenanceTemplate updateTemplate(Long id,
                                              Long clientVersion,
                                              String templateName,
                                              TemplateType templateType,
                                              Double defaultDurationHours,
                                              List<Long> modelIds,
                                              List<String> checklistDescriptions) {

        MaintenanceTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Maintenance template with id '" + id + "' not found."));

        if (!template.getVersion().equals(clientVersion)) {
            throw new ObjectOptimisticLockingFailureException(MaintenanceTemplate.class, id);
        }

        String resolvedName = templateName != null ? templateName : template.getTemplateName();
        TemplateType resolvedType = templateType != null ? templateType : template.getTemplateType();
        Double resolvedDuration = defaultDurationHours != null ? defaultDurationHours : template.getDefaultDurationHours();

        if (!resolvedName.equals(template.getTemplateName())
                && templateRepository.existsByTemplateName(resolvedName)) {
            throw new IllegalStateException(
                    "A maintenance template with name '" + resolvedName + "' already exists.");
        }

        template.updateDetails(resolvedName, resolvedType, resolvedDuration);

        if (modelIds != null) {
            template.updateApplicableModels(resolveModels(modelIds));
        }

        if (checklistDescriptions != null) {
            template.replaceChecklist(checklistDescriptions);
        }

        return templateRepository.save(template);
    }

    private List<AircraftModel> resolveModels(List<Long> modelIds) {
        return modelIds.stream()
                .map(modelId -> aircraftModelRepository.findById(modelId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Aircraft model with id '" + modelId + "' not found.")))
                .toList();
    }
}
