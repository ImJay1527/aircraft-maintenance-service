package pt.isep.sidis.aircraft.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MaintenanceRecordTest {

    private AircraftModel a320;
    private AircraftModel b777;
    private Aircraft aircraft;
    private MaintenanceTemplate template;

    @BeforeEach
    void setUp() {
        a320 = new AircraftModel("A320neo", "Airbus", 160, 24000, 6300, 828);
        b777 = new AircraftModel("777X", "Boeing", 400, 35000, 8000, 900);
        aircraft = new Aircraft("CS-TPA", a320, LocalDate.of(2022, 1, 1), AircraftStatus.AVAILABLE);
        template = new MaintenanceTemplate(
                "A-Check",
                TemplateType.INSPECTION,
                8.0,
                List.of(a320),
                List.of("Check airframe", "Check avionics")
        );
    }

    @Test
    void testCreateValidRecord() {
        MaintenanceRecord record = new MaintenanceRecord(
                aircraft,
                template,
                "Routine pre-flight maintenance",
                LocalDate.now(),
                null,
                MaintenanceComponent.AIRFRAME,
                1500.0
        );

        assertEquals(aircraft, record.getAircraft());
        assertEquals(template, record.getTemplate());
        assertEquals("Routine pre-flight maintenance", record.getDescription());
        assertEquals(LocalDate.now(), record.getStartDate());
        assertEquals(8.0, record.getExpectedDurationHours()); // falls back to template default
        assertEquals(MaintenanceComponent.AIRFRAME, record.getComponent());
        assertEquals(1500.0, record.getEstimatedCost());
        assertEquals(MaintenanceStatus.PLANNED, record.getStatus());
        assertNotNull(record.getCreatedAt());
        assertNull(record.getCompletedAt());
        assertTrue(record.isOngoing());
        assertEquals(2, record.getChecklist().size());
    }

    @Test
    void testTemplateNotApplicableToAircraftModelThrowsException() {
        Aircraft boeing = new Aircraft("CS-TPB", b777, LocalDate.now(), AircraftStatus.AVAILABLE);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new MaintenanceRecord(boeing, template, "Check", LocalDate.now(), 5.0, MaintenanceComponent.ENGINE, 1000.0));

        assertTrue(ex.getMessage().contains("not applicable"));
    }

    @Test
    void testConstructorValidations() {
        assertThrows(IllegalArgumentException.class, () ->
                new MaintenanceRecord(null, template, "Check", LocalDate.now(), 5.0, MaintenanceComponent.ENGINE, 1000.0));

        assertThrows(IllegalArgumentException.class, () ->
                new MaintenanceRecord(aircraft, null, "Check", LocalDate.now(), 5.0, MaintenanceComponent.ENGINE, 1000.0));

        assertThrows(IllegalArgumentException.class, () ->
                new MaintenanceRecord(aircraft, template, "", LocalDate.now(), 5.0, MaintenanceComponent.ENGINE, 1000.0));

        assertThrows(IllegalArgumentException.class, () ->
                new MaintenanceRecord(aircraft, template, "Check", null, 5.0, MaintenanceComponent.ENGINE, 1000.0));

        assertThrows(IllegalArgumentException.class, () ->
                new MaintenanceRecord(aircraft, template, "Check", LocalDate.now(), 5.0, null, 1000.0));

        assertThrows(IllegalArgumentException.class, () ->
                new MaintenanceRecord(aircraft, template, "Check", LocalDate.now(), 5.0, MaintenanceComponent.ENGINE, -100.0));
    }

    @Test
    void testLifecycleStartAndComplete() {
        MaintenanceRecord record = new MaintenanceRecord(
                aircraft, template, "Check", LocalDate.now(), 10.0, MaintenanceComponent.AIRFRAME, 2000.0
        );

        assertEquals(MaintenanceStatus.PLANNED, record.getStatus());

        // Start work
        record.startWork();
        assertEquals(MaintenanceStatus.IN_PROGRESS, record.getStatus());
        assertTrue(record.isOngoing());

        // Complete work
        record.markAsCompleted("Completed with zero defects.", 9.0, 1950.0);
        assertEquals(MaintenanceStatus.COMPLETED, record.getStatus());
        assertFalse(record.isOngoing());
        assertEquals("Completed with zero defects.", record.getCompletionNotes());
        assertEquals(9.0, record.getActualDurationHours());
        assertEquals(1950.0, record.getActualCost());
        assertNotNull(record.getCompletedAt());
    }

    @Test
    void testCannotStartNonPlannedRecord() {
        MaintenanceRecord record = new MaintenanceRecord(
                aircraft, template, "Check", LocalDate.now(), 10.0, MaintenanceComponent.AIRFRAME, 2000.0
        );
        record.startWork();

        assertThrows(IllegalStateException.class, record::startWork);
    }

    @Test
    void testCannotCompleteNonInProgressRecord() {
        MaintenanceRecord record = new MaintenanceRecord(
                aircraft, template, "Check", LocalDate.now(), 10.0, MaintenanceComponent.AIRFRAME, 2000.0
        );

        assertThrows(IllegalStateException.class, () ->
                record.markAsCompleted("Done", 5.0, 100.0));
    }

    @Test
    void testCompleteValidations() {
        MaintenanceRecord record = new MaintenanceRecord(
                aircraft, template, "Check", LocalDate.now(), 10.0, MaintenanceComponent.AIRFRAME, 2000.0
        );
        record.startWork();

        assertThrows(IllegalArgumentException.class, () ->
                record.markAsCompleted("", 5.0, 100.0));

        assertThrows(IllegalArgumentException.class, () ->
                record.markAsCompleted(null, 5.0, 100.0));

        assertThrows(IllegalArgumentException.class, () ->
                record.markAsCompleted("Done", 0.0, 100.0));

        assertThrows(IllegalArgumentException.class, () ->
                record.markAsCompleted("Done", -2.0, 100.0));

        assertThrows(IllegalArgumentException.class, () ->
                record.markAsCompleted("Done", 5.0, -50.0));
    }

    @Test
    void testCancelRecord() {
        MaintenanceRecord record = new MaintenanceRecord(
                aircraft, template, "Check", LocalDate.now(), 10.0, MaintenanceComponent.AIRFRAME, 2000.0
        );

        record.cancel("Parts unavailable");
        assertEquals(MaintenanceStatus.CANCELED, record.getStatus());
        assertFalse(record.isOngoing());
        assertTrue(record.getCompletionNotes().contains("Parts unavailable"));
        assertNotNull(record.getCompletedAt());

        // Cannot cancel already canceled record
        assertThrows(IllegalStateException.class, () -> record.cancel("Again"));
    }

    @Test
    void testCannotCancelCompletedRecord() {
        MaintenanceRecord record = new MaintenanceRecord(
                aircraft, template, "Check", LocalDate.now(), 10.0, MaintenanceComponent.AIRFRAME, 2000.0
        );
        record.startWork();
        record.markAsCompleted("Done", 8.0, 1000.0);

        assertThrows(IllegalStateException.class, () -> record.cancel("Too late"));
    }

    @Test
    void testCompleteChecklistItem() {
        MaintenanceRecord record = new MaintenanceRecord(
                aircraft, template, "Check", LocalDate.now(), 10.0, MaintenanceComponent.AIRFRAME, 2000.0
        );

        assertFalse(record.getChecklist().get(0).isCompleted());
        record.completeChecklistItem(0);
        assertTrue(record.getChecklist().get(0).isCompleted());
        assertFalse(record.getChecklist().get(1).isCompleted());
    }

    @Test
    void testScheduleNextMaintenance() {
        MaintenanceRecord record = new MaintenanceRecord(
                aircraft, template, "Check", LocalDate.now(), 10.0, MaintenanceComponent.AIRFRAME, 2000.0
        );

        LocalDate nextDate = LocalDate.now().plusMonths(6);
        record.scheduleNextMaintenance(nextDate, 500.0);

        assertEquals(nextDate, record.getNextMaintenanceDueDate());
        assertEquals(500.0, record.getNextMaintenanceDueHours());

        assertThrows(IllegalArgumentException.class, () ->
                record.scheduleNextMaintenance(null, null));

        assertThrows(IllegalArgumentException.class, () ->
                record.scheduleNextMaintenance(nextDate, -10.0));
    }

    @Test
    void testAdjustExpectedDuration() {
        MaintenanceRecord record = new MaintenanceRecord(
                aircraft, template, "Check", LocalDate.now(), 10.0, MaintenanceComponent.AIRFRAME, 2000.0
        );

        record.adjustExpectedDuration(14.0);
        assertEquals(14.0, record.getExpectedDurationHours());

        assertThrows(IllegalArgumentException.class, () -> record.adjustExpectedDuration(0.0));
        assertThrows(IllegalArgumentException.class, () -> record.adjustExpectedDuration(-3.0));

        record.startWork();
        assertThrows(IllegalStateException.class, () -> record.adjustExpectedDuration(20.0));
    }
}
