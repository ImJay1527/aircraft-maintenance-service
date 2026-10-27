package pt.isep.sidis.aircraft.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import pt.isep.sidis.aircraft.common.exceptions.ResourceNotFoundException;
import pt.isep.sidis.aircraft.domain.AircraftModel;
import pt.isep.sidis.aircraft.domain.MaintenanceTemplate;
import pt.isep.sidis.aircraft.domain.TemplateType;
import pt.isep.sidis.aircraft.repositories.AircraftModelRepository;
import pt.isep.sidis.aircraft.repositories.MaintenanceTemplateRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaintenanceTemplateServiceTest {

    @Mock
    private MaintenanceTemplateRepository templateRepository;

    @Mock
    private AircraftModelRepository aircraftModelRepository;

    @InjectMocks
    private MaintenanceTemplateService service;

    private AircraftModel a320;
    private MaintenanceTemplate template;

    @BeforeEach
    void setUp() {
        a320 = new AircraftModel("A320neo", "Airbus", 160, 24000, 6300, 828);
        a320.setId(1L);
        template = new MaintenanceTemplate("A-Check", TemplateType.INSPECTION, 8.0, List.of(a320), List.of("Check airframe"));
        template.setId(10L);
        template.setVersion(0L);
    }

    @Test
    void testCreateTemplateSuccess() {
        when(templateRepository.existsByTemplateName("New Template")).thenReturn(false);
        when(aircraftModelRepository.findById(1L)).thenReturn(Optional.of(a320));
        when(templateRepository.save(any(MaintenanceTemplate.class))).thenReturn(template);

        MaintenanceTemplate created = service.createTemplate(
                "New Template", TemplateType.INSPECTION, 8.0, List.of(1L), List.of("Check step")
        );

        assertNotNull(created);
        verify(templateRepository).save(any(MaintenanceTemplate.class));
    }

    @Test
    void testCreateTemplateDuplicateNameThrowsException() {
        when(templateRepository.existsByTemplateName("A-Check")).thenReturn(true);

        assertThrows(IllegalStateException.class, () ->
                service.createTemplate("A-Check", TemplateType.INSPECTION, 8.0, List.of(1L), List.of("Check step")));
        verify(templateRepository, never()).save(any());
    }

    @Test
    void testCreateTemplateModelNotFoundThrowsException() {
        when(templateRepository.existsByTemplateName("New Template")).thenReturn(false);
        when(aircraftModelRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                service.createTemplate("New Template", TemplateType.INSPECTION, 8.0, List.of(99L), List.of("Check step")));
    }

    @Test
    void testGetTemplateByIdSuccess() {
        when(templateRepository.findById(10L)).thenReturn(Optional.of(template));

        MaintenanceTemplate result = service.getTemplateById(10L);
        assertNotNull(result);
        assertEquals("A-Check", result.getTemplateName());
    }

    @Test
    void testGetTemplateByIdNotFoundThrowsException() {
        when(templateRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getTemplateById(99L));
    }

    @Test
    void testGetAllTemplates() {
        when(templateRepository.findAll()).thenReturn(List.of(template));

        List<MaintenanceTemplate> list = service.getAllTemplates();
        assertEquals(1, list.size());
    }

    @Test
    void testGetTemplatesByType() {
        when(templateRepository.findByTemplateType(TemplateType.INSPECTION)).thenReturn(List.of(template));

        List<MaintenanceTemplate> list = service.getTemplatesByType(TemplateType.INSPECTION);
        assertEquals(1, list.size());
    }

    @Test
    void testGetTemplatesForModel() {
        when(aircraftModelRepository.findById(1L)).thenReturn(Optional.of(a320));
        when(templateRepository.findByApplicableModel(1L)).thenReturn(List.of(template));

        List<MaintenanceTemplate> list = service.getTemplatesForModel(1L);
        assertEquals(1, list.size());
    }

    @Test
    void testGetTemplatesForModelNotFoundThrowsException() {
        when(aircraftModelRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getTemplatesForModel(99L));
    }

    @Test
    void testUpdateTemplateSuccess() {
        when(templateRepository.findById(10L)).thenReturn(Optional.of(template));
        when(templateRepository.save(any(MaintenanceTemplate.class))).thenReturn(template);

        MaintenanceTemplate updated = service.updateTemplate(
                10L, 0L, "Updated A-Check", TemplateType.OVERHAUL, 24.0, null, null
        );

        assertNotNull(updated);
        verify(templateRepository).save(template);
    }

    @Test
    void testUpdateTemplateVersionMismatchThrowsOptimisticLockException() {
        when(templateRepository.findById(10L)).thenReturn(Optional.of(template));

        assertThrows(ObjectOptimisticLockingFailureException.class, () ->
                service.updateTemplate(10L, 5L, "Updated Name", TemplateType.OVERHAUL, 24.0, null, null));
    }

    @Test
    void testUpdateTemplateDuplicateNameThrowsException() {
        when(templateRepository.findById(10L)).thenReturn(Optional.of(template));
        when(templateRepository.existsByTemplateName("Existing Name")).thenReturn(true);

        assertThrows(IllegalStateException.class, () ->
                service.updateTemplate(10L, 0L, "Existing Name", TemplateType.OVERHAUL, 24.0, null, null));
    }
}
