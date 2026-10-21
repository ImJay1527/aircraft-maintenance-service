package pt.isep.sidis.aircraft.services;

import org.springframework.stereotype.Service;
import pt.isep.sidis.aircraft.domain.Aircraft;
import pt.isep.sidis.aircraft.repositories.AircraftRepository;
import pt.isep.sidis.aircraft.common.exceptions.ResourceNotFoundException;

@Service
public class AircraftService {

    private final AircraftRepository aircraftRepository;

    public AircraftService(AircraftRepository aircraftRepository) {
        this.aircraftRepository = aircraftRepository;
    }

    public Aircraft getAircraft(String registrationNumber) {
        return aircraftRepository.findByRegistrationNumber(registrationNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Aircraft not found with registration: " + registrationNumber));
    }
    
    public Aircraft createAircraft(Aircraft aircraft) {
        return aircraftRepository.save(aircraft);
    }
}
