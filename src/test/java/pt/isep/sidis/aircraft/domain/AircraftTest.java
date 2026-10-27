package pt.isep.sidis.aircraft.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class AircraftTest {

    private AircraftModel model;
    private Aircraft aircraft;

    @BeforeEach
    void setUp() {
        model = new AircraftModel("A320neo", "Airbus", 160, 24000, 6300, 828);
        aircraft = new Aircraft("CS-TPA", model, LocalDate.of(2023, 1, 15), AircraftStatus.AVAILABLE);
    }

    @Test
    void testAircraftInitialization() {
        assertEquals("CS-TPA", aircraft.getRegistrationNumber());
        assertEquals(model, aircraft.getModel());
        assertEquals(LocalDate.of(2023, 1, 15), aircraft.getManufacturingDate());
        assertEquals(AircraftStatus.AVAILABLE, aircraft.getStatus());
        assertEquals("Economy", aircraft.getActiveConfigurationName());
        assertEquals(160, aircraft.getActiveCapacity());
        assertEquals(0.0, aircraft.getTotalFlightHours());
        assertEquals(0, aircraft.getNumberOfAssignments());
    }

    @Test
    void testRegistrationNumberIsNormalizedToUpperCase() {
        Aircraft lowerReg = new Aircraft("cs-tpb", model, LocalDate.now(), AircraftStatus.AVAILABLE);
        assertEquals("CS-TPB", lowerReg.getRegistrationNumber());
    }

    @Test
    void testAddFlightHours() {
        aircraft.addFlightHours(3.5);
        assertEquals(3.5, aircraft.getTotalFlightHours());

        aircraft.addFlightHours(2.0);
        assertEquals(5.5, aircraft.getTotalFlightHours());
    }

    @Test
    void testAddFlightHoursIgnoresNullOrNegative() {
        aircraft.addFlightHours(null);
        assertEquals(0.0, aircraft.getTotalFlightHours());

        aircraft.addFlightHours(-4.0);
        assertEquals(0.0, aircraft.getTotalFlightHours());

        aircraft.addFlightHours(0.0);
        assertEquals(0.0, aircraft.getTotalFlightHours());
    }

    @Test
    void testAddAssignment() {
        assertEquals(0, aircraft.getNumberOfAssignments());
        aircraft.addAssignment();
        assertEquals(1, aircraft.getNumberOfAssignments());
        aircraft.addAssignment();
        assertEquals(2, aircraft.getNumberOfAssignments());
    }

    @Test
    void testUpdateStatus() {
        aircraft.updateStatus(AircraftStatus.IN_FLIGHT);
        assertEquals(AircraftStatus.IN_FLIGHT, aircraft.getStatus());

        aircraft.updateStatus(AircraftStatus.UNDER_MAINTENANCE);
        assertEquals(AircraftStatus.UNDER_MAINTENANCE, aircraft.getStatus());

        aircraft.updateStatus(AircraftStatus.AVAILABLE);
        assertEquals(AircraftStatus.AVAILABLE, aircraft.getStatus());

        aircraft.updateStatus(AircraftStatus.INACTIVE);
        assertEquals(AircraftStatus.INACTIVE, aircraft.getStatus());
    }

    @Test
    void testUpdateStatusIgnoresNull() {
        aircraft.updateStatus(null);
        assertEquals(AircraftStatus.AVAILABLE, aircraft.getStatus());
    }

    @Test
    void testCustomConfigurationAndCapacity() {
        Aircraft custom = new Aircraft("CS-TPZ", model, LocalDate.now(), AircraftStatus.AVAILABLE, "Business-Flex", 120);
        assertEquals("Business-Flex", custom.getActiveConfigurationName());
        assertEquals(120, custom.getActiveCapacity());
    }

    @Test
    void testSettersAndGetters() {
        aircraft.setRegistrationNumber("CS-NEW");
        assertEquals("CS-NEW", aircraft.getRegistrationNumber());

        aircraft.setActiveConfigurationName("First");
        assertEquals("First", aircraft.getActiveConfigurationName());

        aircraft.setActiveCapacity(90);
        assertEquals(90, aircraft.getActiveCapacity());

        aircraft.setTotalFlightHours(150.0);
        assertEquals(150.0, aircraft.getTotalFlightHours());

        aircraft.setNumberOfAssignments(12);
        assertEquals(12, aircraft.getNumberOfAssignments());

        aircraft.setVersion(1L);
        assertEquals(1L, aircraft.getVersion());
    }
}
