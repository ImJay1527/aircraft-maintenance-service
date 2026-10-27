package pt.isep.sidis.aircraft.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MaintenanceTemplateTest {

    private AircraftModel a320;
    private AircraftModel b737;

    @BeforeEach
    void setUp() {
        a320 = new AircraftModel("A320neo", "Airbus", 160, 24000, 6300, 828);
        b737 = new AircraftModel("737 MAX", "Boeing", 180, 26000, 6500, 840);
    }

    @Test
    void testCreateValidTemplate() {
        MaintenanceTemplate template = new MaintenanceTemplate(
                "A-Check Routine",
                TemplateType.INSPECTION,
                8.0,
                List.of(a320, b737),
                List.of("Check tyres", "Inspect fuselage")
        );

        assertEquals("A-Check Routine", template.getTemplateName());
        assertEquals(TemplateType.INSPECTION, template.getTemplateType());
        assertEquals(8.0, template.getDefaultDurationHours());
        assertEquals(2, template.getApplicableModels().size());
        assertEquals(2, template.getChecklist().size());
        assertEquals("Check tyres", template.getChecklist().get(0).getDescription());
        assertFalse(template.getChecklist().get(0).isCompleted());
    }

    @Test
    void testBlankTemplateNameThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
                new MaintenanceTemplate("", TemplateType.INSPECTION, 8.0, List.of(a320), List.of("Check")));
        assertThrows(IllegalArgumentException.class, () ->
                new MaintenanceTemplate(null, TemplateType.INSPECTION, 8.0, List.of(a320), List.of("Check")));
    }

    @Test
    void testNullTemplateTypeThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
                new MaintenanceTemplate("Check", null, 8.0, List.of(a320), List.of("Check")));
    }

    @Test
    void testInvalidDurationThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
                new MaintenanceTemplate("Check", TemplateType.INSPECTION, 0.0, List.of(a320), List.of("Check")));
        assertThrows(IllegalArgumentException.class, () ->
                new MaintenanceTemplate("Check", TemplateType.INSPECTION, -5.0, List.of(a320), List.of("Check")));
        assertThrows(IllegalArgumentException.class, () ->
                new MaintenanceTemplate("Check", TemplateType.INSPECTION, null, List.of(a320), List.of("Check")));
    }

    @Test
    void testEmptyApplicableModelsThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
                new MaintenanceTemplate("Check", TemplateType.INSPECTION, 8.0, List.of(), List.of("Check")));
        assertThrows(IllegalArgumentException.class, () ->
                new MaintenanceTemplate("Check", TemplateType.INSPECTION, 8.0, null, List.of("Check")));
    }

    @Test
    void testEmptyChecklistThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
                new MaintenanceTemplate("Check", TemplateType.INSPECTION, 8.0, List.of(a320), List.of()));
        assertThrows(IllegalArgumentException.class, () ->
                new MaintenanceTemplate("Check", TemplateType.INSPECTION, 8.0, List.of(a320), null));
    }

    @Test
    void testIsApplicableTo() {
        MaintenanceTemplate template = new MaintenanceTemplate(
                "A320 Specific Check",
                TemplateType.INSPECTION,
                4.0,
                List.of(a320),
                List.of("A320 fly-by-wire check")
        );

        assertTrue(template.isApplicableTo(a320));
        assertFalse(template.isApplicableTo(b737));
        assertFalse(template.isApplicableTo(null));
    }

    @Test
    void testCloneChecklistReturnsIndependentCopy() {
        MaintenanceTemplate template = new MaintenanceTemplate(
                "C-Check",
                TemplateType.SCHEDULED_MAINTENANCE,
                72.0,
                List.of(a320),
                List.of("Step 1", "Step 2")
        );

        List<ChecklistItem> clone = template.cloneChecklist();
        assertEquals(2, clone.size());

        // Modifying clone does not affect template's internal list
        clone.get(0).markDone();
        assertTrue(clone.get(0).isCompleted());
        assertFalse(template.getChecklist().get(0).isCompleted());
    }

    @Test
    void testUpdateDetails() {
        MaintenanceTemplate template = new MaintenanceTemplate(
                "Old Name",
                TemplateType.INSPECTION,
                5.0,
                List.of(a320),
                List.of("Step 1")
        );

        template.updateDetails("New Name", TemplateType.OVERHAUL, 50.0);
        assertEquals("New Name", template.getTemplateName());
        assertEquals(TemplateType.OVERHAUL, template.getTemplateType());
        assertEquals(50.0, template.getDefaultDurationHours());
    }

    @Test
    void testUpdateApplicableModels() {
        MaintenanceTemplate template = new MaintenanceTemplate(
                "Inspection",
                TemplateType.INSPECTION,
                5.0,
                List.of(a320),
                List.of("Step 1")
        );

        template.updateApplicableModels(List.of(b737));
        assertFalse(template.isApplicableTo(a320));
        assertTrue(template.isApplicableTo(b737));

        assertThrows(IllegalArgumentException.class, () -> template.updateApplicableModels(List.of()));
        assertThrows(IllegalArgumentException.class, () -> template.updateApplicableModels(null));
    }

    @Test
    void testReplaceChecklist() {
        MaintenanceTemplate template = new MaintenanceTemplate(
                "Inspection",
                TemplateType.INSPECTION,
                5.0,
                List.of(a320),
                List.of("Old Step")
        );

        template.replaceChecklist(List.of("New Step A", "New Step B"));
        assertEquals(2, template.getChecklist().size());
        assertEquals("New Step A", template.getChecklist().get(0).getDescription());

        assertThrows(IllegalArgumentException.class, () -> template.replaceChecklist(List.of()));
        assertThrows(IllegalArgumentException.class, () -> template.replaceChecklist(null));
    }
}
