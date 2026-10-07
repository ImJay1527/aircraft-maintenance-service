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
        // Se a BD local não tem, perguntamos aos peers da rede P2P
        Optional<Aircraft> remoteAircraft = replicationClient.forwardGetAircraft(registrationNumber);
        
        if (remoteAircraft.isPresent()) {
            return remoteAircraft.get();
        }

        // Se nem os peers têm, então a aeronave realmente não existe
        throw new ResourceNotFoundException("Aircraft not found locally or in any reachable peer.");
    }
    
    public Aircraft createAircraft(Aircraft aircraft) {
        return aircraftRepository.save(aircraft);
    }
}
