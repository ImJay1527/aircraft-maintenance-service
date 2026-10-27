package pt.isep.sidis.aircraft.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pt.isep.sidis.aircraft.common.exceptions.ResourceNotFoundException;
import pt.isep.sidis.aircraft.domain.Aircraft;
import pt.isep.sidis.aircraft.domain.AircraftModel;
import pt.isep.sidis.aircraft.domain.AircraftStatus;
import pt.isep.sidis.aircraft.repositories.AircraftRepository;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AircraftServiceTest {

    @Mock
    private AircraftRepository aircraftRepository;

    @InjectMocks
    private AircraftService aircraftService;

    private Aircraft aircraft;

    @BeforeEach
    void setUp() {
        AircraftModel model = new AircraftModel("A320neo", "Airbus", 160, 24000, 6300, 828);
        aircraft = new Aircraft("CS-TPA", model, LocalDate.of(2023, 1, 1), AircraftStatus.AVAILABLE);
    }

    @Test
    void testGetAircraftSuccess() {
        when(aircraftRepository.findByRegistrationNumber("CS-TPA")).thenReturn(Optional.of(aircraft));

        Aircraft result = aircraftService.getAircraft("CS-TPA");

        assertNotNull(result);
        assertEquals("CS-TPA", result.getRegistrationNumber());
        assertEquals("A320neo", result.getModel().getName());
        verify(aircraftRepository, times(1)).findByRegistrationNumber("CS-TPA");
    }

    @Test
    void testGetAircraftNotFoundThrowsException() {
        when(aircraftRepository.findByRegistrationNumber("CS-UNKNOWN")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                aircraftService.getAircraft("CS-UNKNOWN"));

        verify(aircraftRepository, times(1)).findByRegistrationNumber("CS-UNKNOWN");
    }

    @Test
    void testCreateAircraft() {
        when(aircraftRepository.save(any(Aircraft.class))).thenReturn(aircraft);

        Aircraft created = aircraftService.createAircraft(aircraft);

        assertNotNull(created);
        assertEquals("CS-TPA", created.getRegistrationNumber());
        verify(aircraftRepository, times(1)).save(aircraft);
    }
}
