package pt.isep.sidis.aircraft.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import pt.isep.sidis.aircraft.common.exceptions.ResourceNotFoundException;
import pt.isep.sidis.aircraft.domain.Aircraft;
import pt.isep.sidis.aircraft.domain.AircraftModel;
import pt.isep.sidis.aircraft.domain.AircraftStatus;
import pt.isep.sidis.aircraft.domain.MaintenanceComponent;
import pt.isep.sidis.aircraft.domain.MaintenanceRecord;
import pt.isep.sidis.aircraft.domain.MaintenanceStatus;
import pt.isep.sidis.aircraft.domain.MaintenanceTemplate;
import pt.isep.sidis.aircraft.domain.TemplateType;
import pt.isep.sidis.aircraft.repositories.AircraftRepository;
import pt.isep.sidis.aircraft.repositories.MaintenanceRecordRepository;
import pt.isep.sidis.aircraft.repositories.MaintenanceTemplateRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaintenanceRecordServiceTest {

    @Mock
    private MaintenanceRecordRepository recordRepository;

    @Mock
    private MaintenanceTemplateRepository templateRepository;

    @Mock
    private AircraftRepository aircraftRepository;

    @InjectMocks
    private MaintenanceRecordService service;

    private Aircraft aircraft;
    private MaintenanceTemplate template;
    private MaintenanceRecord record;

    @BeforeEach
    void setUp() {
        AircraftModel model = new AircraftModel("A320neo", "Airbus", 160, 24000, 6300, 828);
        aircraft = new Aircraft("CS-TPA", model, LocalDate.of(2022, 1, 1), AircraftStatus.AVAILABLE);
        template = new MaintenanceTemplate("A-Check", TemplateType.INSPECTION, 8.0, List.of(model), List.of("Visual inspection"));
        template.setId(1L);

        record = new MaintenanceRecord(aircraft, template, "Inspection", LocalDate.now(), 8.0, MaintenanceComponent.AIRFRAME, 1200.0);
        record.setId(100L);
        record.setVersion(0L);
    }

    @Test
    void testCreateRecordSuccess() {
        when(aircraftRepository.findById("CS-TPA")).thenReturn(Optional.of(aircraft));
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(recordRepository.save(any(MaintenanceRecord.class))).thenReturn(record);

        MaintenanceRecord created = service.createRecord(
                "CS-TPA", 1L, "Inspection", LocalDate.now(), 8.0, MaintenanceComponent.AIRFRAME, 1200.0
        );

        assertNotNull(created);
        assertEquals(AircraftStatus.UNDER_MAINTENANCE, aircraft.getStatus());
        verify(aircraftRepository).save(aircraft);
        verify(recordRepository).save(any(MaintenanceRecord.class));
    }

    @Test
    void testCreateRecordAircraftNotFoundThrowsException() {
        when(aircraftRepository.findById("CS-UNKNOWN")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                service.createRecord("CS-UNKNOWN", 1L, "Check", LocalDate.now(), 8.0, MaintenanceComponent.AIRFRAME, 1200.0));
    }

    @Test
    void testCreateRecordAircraftAlreadyUnderMaintenanceThrowsException() {
        aircraft.setStatus(AircraftStatus.UNDER_MAINTENANCE);
        when(aircraftRepository.findById("CS-TPA")).thenReturn(Optional.of(aircraft));

        assertThrows(IllegalStateException.class, () ->
                service.createRecord("CS-TPA", 1L, "Check", LocalDate.now(), 8.0, MaintenanceComponent.AIRFRAME, 1200.0));
    }

    @Test
    void testCreateRecordTemplateNotFoundThrowsException() {
        when(aircraftRepository.findById("CS-TPA")).thenReturn(Optional.of(aircraft));
        when(templateRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                service.createRecord("CS-TPA", 99L, "Check", LocalDate.now(), 8.0, MaintenanceComponent.AIRFRAME, 1200.0));
    }

    @Test
    void testStartRecordSuccess() {
        when(recordRepository.findById(100L)).thenReturn(Optional.of(record));
        when(recordRepository.save(record)).thenReturn(record);

        MaintenanceRecord started = service.startRecord(100L, 0L);

        assertEquals(MaintenanceStatus.IN_PROGRESS, started.getStatus());
        verify(recordRepository).save(record);
    }

    @Test
    void testStartRecordVersionMismatchThrowsOptimisticLockException() {
        when(recordRepository.findById(100L)).thenReturn(Optional.of(record));

        assertThrows(ObjectOptimisticLockingFailureException.class, () ->
                service.startRecord(100L, 5L));
    }

    @Test
    void testCompleteRecordSuccess() {
        record.startWork();
        aircraft.setStatus(AircraftStatus.UNDER_MAINTENANCE);
        when(recordRepository.findById(100L)).thenReturn(Optional.of(record));
        when(recordRepository.save(record)).thenReturn(record);

        MaintenanceRecord completed = service.completeRecord(
                100L, 0L, "Inspected and certified airworthy.", 7.5, 1150.0
        );

        assertEquals(MaintenanceStatus.COMPLETED, completed.getStatus());
        assertEquals(AircraftStatus.AVAILABLE, aircraft.getStatus());
        verify(aircraftRepository).save(aircraft);
        verify(recordRepository).save(record);
    }

    @Test
    void testCompleteRecordVersionMismatchThrowsOptimisticLockException() {
        when(recordRepository.findById(100L)).thenReturn(Optional.of(record));

        assertThrows(ObjectOptimisticLockingFailureException.class, () ->
                service.completeRecord(100L, 9L, "Notes", 5.0, 100.0));
    }

    @Test
    void testCancelRecordSuccess() {
        aircraft.setStatus(AircraftStatus.UNDER_MAINTENANCE);
        when(recordRepository.findById(100L)).thenReturn(Optional.of(record));
        when(recordRepository.save(record)).thenReturn(record);

        MaintenanceRecord cancelled = service.cancelRecord(100L, 0L, "Flight cancellation");

        assertEquals(MaintenanceStatus.CANCELED, cancelled.getStatus());
        assertEquals(AircraftStatus.AVAILABLE, aircraft.getStatus());
        verify(aircraftRepository).save(aircraft);
        verify(recordRepository).save(record);
    }

    @Test
    void testGetRecordsForAircraft() {
        when(aircraftRepository.existsById("CS-TPA")).thenReturn(true);
        when(recordRepository.findByAircraft_RegistrationNumber("CS-TPA")).thenReturn(List.of(record));

        List<MaintenanceRecord> records = service.getRecordsForAircraft("CS-TPA");
        assertEquals(1, records.size());
    }

    @Test
    void testGetRecordsForNonexistentAircraftThrowsException() {
        when(aircraftRepository.existsById("CS-UNKNOWN")).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () ->
                service.getRecordsForAircraft("CS-UNKNOWN"));
    }

    @Test
    void testGetOngoingActivities() {
        when(recordRepository.findAllOngoing()).thenReturn(List.of(record));

        List<MaintenanceRecord> ongoing = service.getOngoingActivities();
        assertEquals(1, ongoing.size());
    }

    @Test
    void testGetTotalMaintenanceHoursPerAircraft() {
        Object[] row = new Object[]{"CS-TPA", 45.5};
        when(recordRepository.findTotalMaintenanceHoursPerAircraft()).thenReturn(java.util.Collections.singletonList(row));

        var report = service.getTotalMaintenanceHoursPerAircraft();
        assertEquals(1, report.size());
        assertEquals("CS-TPA", report.get(0).registrationNumber());
        assertEquals(45.5, report.get(0).totalHours());
    }

    @Test
    void testGetCostReportPerAircraft() {
        Object[] row = new Object[]{"CS-TPA", 2000.0, 1850.0};
        when(recordRepository.findCostReportPerAircraft()).thenReturn(java.util.Collections.singletonList(row));

        var report = service.getCostReportPerAircraft();
        assertEquals(1, report.size());
        assertEquals("CS-TPA", report.get(0).key());
        assertEquals(2000.0, report.get(0).estimatedCost());
        assertEquals(1850.0, report.get(0).actualCost());
    }

    @Test
    void testGetAverageTurnaroundPerAircraftModel() {
        Object[] row = new Object[]{"A320neo", 12.5};
        when(recordRepository.findAverageTurnaroundPerAircraftModel()).thenReturn(java.util.Collections.singletonList(row));

        var report = service.getAverageTurnaroundPerAircraftModel();
        assertEquals(1, report.size());
        assertEquals("A320neo", report.get(0).modelName());
        assertEquals(12.5, report.get(0).avgDurationHours());
    }

    @Test
    void testSearchRecordsValidation() {
        LocalDate from = LocalDate.of(2025, 5, 10);
        LocalDate to = LocalDate.of(2025, 5, 1);

        assertThrows(IllegalArgumentException.class, () ->
                service.searchRecords("CS-TPA", null, from, to));

        assertThrows(IllegalArgumentException.class, () ->
                service.searchRecords("CS-TPA", "INVALID_COMPONENT", null, null));
    }
}
