package pt.isep.sidis.aircraft.services;

import org.springframework.stereotype.Service;
import pt.isep.sidis.aircraft.domain.Aircraft;
import pt.isep.sidis.aircraft.repositories.AircraftRepository;
import pt.isep.sidis.aircraft.common.exceptions.ResourceNotFoundException;

import java.util.Optional;

@Service
public class AircraftService {

    private final AircraftRepository aircraftRepository;

    public AircraftService(AircraftRepository aircraftRepository) {
        this.aircraftRepository = aircraftRepository;
    }

    public Aircraft getAircraft(String registrationNumber) {
        Optional<Aircraft> aircraft = aircraftRepository.findByRegistrationNumber(registrationNumber);
        
        if (aircraft.isPresent()) {
            return aircraft.get();
        }

        // TODO: Week 3 - HTTP Forwarding Logic (P2P) will be injected here.
        // If not found locally, we should query peers before throwing the exception.
        throw new ResourceNotFoundException("Aircraft not found locally.");
    }
    
    public Aircraft createAircraft(Aircraft aircraft) {
        return aircraftRepository.save(aircraft);
    }
}
