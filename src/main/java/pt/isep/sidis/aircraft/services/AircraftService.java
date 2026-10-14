package pt.isep.sidis.aircraft.services;

import org.springframework.stereotype.Service;
import pt.isep.sidis.aircraft.domain.Aircraft;
import pt.isep.sidis.aircraft.repositories.AircraftRepository;
import pt.isep.sidis.aircraft.common.exceptions.ResourceNotFoundException;

import java.util.Optional;

@Service
public class AircraftService {

    private final AircraftRepository aircraftRepository;
    private final pt.isep.sidis.aircraft.replication.P2PReplicationClient replicationClient;

    public AircraftService(AircraftRepository aircraftRepository, pt.isep.sidis.aircraft.replication.P2PReplicationClient replicationClient) {
        this.aircraftRepository = aircraftRepository;
        this.replicationClient = replicationClient;
    }

    public Aircraft getAircraft(String registrationNumber) {
        Optional<Aircraft> aircraft = aircraftRepository.findByRegistrationNumber(registrationNumber);
        
        if (aircraft.isPresent()) {
            return aircraft.get();
        }

        // Week 3 - HTTP Forwarding Logic (P2P)
        // If not found in local database, query P2P network peers
        Optional<Aircraft> remoteAircraft = replicationClient.forwardGetAircraft(registrationNumber);
        
        if (remoteAircraft.isPresent()) {
            return remoteAircraft.get();
        }

        // If none of the peers have it, the aircraft does not exist
        throw new ResourceNotFoundException("Aircraft not found locally or in any reachable peer.");
    }
    
    public Aircraft createAircraft(Aircraft aircraft) {
        return aircraftRepository.save(aircraft);
    }
}
