package pt.isep.sidis.aircraft.services;

import org.springframework.stereotype.Service;
import pt.isep.sidis.aircraft.domain.Aircraft;
import pt.isep.sidis.aircraft.messaging.DomainEventPublisher;
import pt.isep.sidis.aircraft.messaging.events.AircraftCreatedEvent;
import pt.isep.sidis.aircraft.repositories.AircraftRepository;
import pt.isep.sidis.aircraft.common.exceptions.ResourceNotFoundException;

@Service
public class AircraftService {

    private final AircraftRepository aircraftRepository;
    private final DomainEventPublisher eventPublisher;

    public AircraftService(AircraftRepository aircraftRepository, DomainEventPublisher eventPublisher) {
        this.aircraftRepository = aircraftRepository;
        this.eventPublisher = eventPublisher;
    }

    public Aircraft getAircraft(String registrationNumber) {
        return aircraftRepository.findByRegistrationNumber(registrationNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Aircraft not found with registration: " + registrationNumber));
    }
    
    public Aircraft createAircraft(Aircraft aircraft) {
        Aircraft saved = aircraftRepository.save(aircraft);
        if (saved.getModel() != null) {
            eventPublisher.publishAircraftCreated(new AircraftCreatedEvent(
                    saved.getRegistrationNumber(),
                    saved.getRegistrationNumber(),
                    saved.getModel().getName(),
                    saved.getModel().getManufacturer(),
                    (double) saved.getModel().getMaximumRange(),
                    (double) saved.getModel().getFuelCapacity(),
                    saved.getModel().getSeatingCapacity()
            ));
        }
        return saved;
    }
}
