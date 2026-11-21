package pt.isep.sidis.aircraft.cqrs.handlers;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.isep.sidis.aircraft.common.exceptions.ResourceNotFoundException;
import pt.isep.sidis.aircraft.cqrs.commands.RegisterAircraftCommand;
import pt.isep.sidis.aircraft.cqrs.commands.UpdateAircraftStatusCommand;
import pt.isep.sidis.aircraft.domain.Aircraft;
import pt.isep.sidis.aircraft.domain.AircraftModel;
import pt.isep.sidis.aircraft.domain.AircraftStatus;
import pt.isep.sidis.aircraft.messaging.DomainEventPublisher;
import pt.isep.sidis.aircraft.messaging.events.AircraftCreatedEvent;
import pt.isep.sidis.aircraft.messaging.events.AircraftStatusChangedEvent;
import pt.isep.sidis.aircraft.repositories.AircraftModelRepository;
import pt.isep.sidis.aircraft.repositories.AircraftRepository;

@Service
public class AircraftCommandHandler {

    private final AircraftRepository aircraftRepository;
    private final AircraftModelRepository modelRepository;
    private final DomainEventPublisher eventPublisher;

    public AircraftCommandHandler(AircraftRepository aircraftRepository,
                                  AircraftModelRepository modelRepository,
                                  DomainEventPublisher eventPublisher) {
        this.aircraftRepository = aircraftRepository;
        this.modelRepository = modelRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Aircraft handle(RegisterAircraftCommand command) {
        AircraftModel model = modelRepository.findByName(command.modelCode())
                .or(() -> {
                    try {
                        return modelRepository.findById(Long.parseLong(command.modelCode()));
                    } catch (NumberFormatException | NullPointerException e) {
                        return java.util.Optional.empty();
                    }
                })
                .orElseThrow(() -> new ResourceNotFoundException("AircraftModel not found: " + command.modelCode()));

        Aircraft aircraft = new Aircraft(command.registration(), model, java.time.LocalDate.now(), AircraftStatus.AVAILABLE);
        Aircraft saved = aircraftRepository.save(aircraft);

        // Publish domain event to broker & local projections
        String fullModelName = model.getManufacturer() != null && !model.getManufacturer().isBlank()
                ? model.getManufacturer() + " " + model.getName()
                : model.getName();

        eventPublisher.publishAircraftCreated(new AircraftCreatedEvent(
                saved.getRegistrationNumber(),
                command.serialNumber(),
                model.getName(),
                fullModelName,
                (double) model.getMaximumRange(),
                (double) model.getFuelCapacity(),
                model.getSeatingCapacity()
        ));

        return saved;
    }

    @Transactional
    public Aircraft handle(UpdateAircraftStatusCommand command) {
        Aircraft aircraft = aircraftRepository.findByRegistrationNumber(command.registration().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Aircraft not found: " + command.registration()));

        String oldStatus = aircraft.getStatus().name();
        AircraftStatus newStatus = AircraftStatus.valueOf(command.status().toUpperCase());
        aircraft.updateStatus(newStatus);
        Aircraft saved = aircraftRepository.save(aircraft);

        eventPublisher.publishAircraftStatusChanged(new AircraftStatusChangedEvent(
                saved.getRegistrationNumber(),
                oldStatus,
                newStatus.name(),
                saved.getTotalFlightHours()
        ));

        return saved;
    }
}
