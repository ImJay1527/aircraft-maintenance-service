package pt.isep.sidis.aircraft.cqrs.handlers;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.isep.sidis.aircraft.cqrs.commands.*;
import pt.isep.sidis.aircraft.domain.AircraftStatus;
import pt.isep.sidis.aircraft.domain.MaintenanceComponent;
import pt.isep.sidis.aircraft.domain.MaintenanceRecord;
import pt.isep.sidis.aircraft.messaging.DomainEventPublisher;
import pt.isep.sidis.aircraft.messaging.events.AircraftStatusChangedEvent;
import pt.isep.sidis.aircraft.messaging.events.MaintenanceCompletedEvent;
import pt.isep.sidis.aircraft.messaging.events.MaintenanceScheduledEvent;
import pt.isep.sidis.aircraft.services.MaintenanceRecordService;

@Service
public class MaintenanceCommandHandler {

    private final MaintenanceRecordService recordService;
    private final DomainEventPublisher eventPublisher;

    public MaintenanceCommandHandler(MaintenanceRecordService recordService,
                                     DomainEventPublisher eventPublisher) {
        this.recordService = recordService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public MaintenanceRecord handle(ScheduleMaintenanceCommand command) {
        MaintenanceComponent component = MaintenanceComponent.valueOf(command.component().toUpperCase());
        MaintenanceRecord record = recordService.createRecord(
                command.registration(),
                command.templateId(),
                command.description(),
                command.scheduledDate(),
                command.estimatedHours(),
                component,
                command.estimatedCost()
        );

        eventPublisher.publishMaintenanceScheduled(new MaintenanceScheduledEvent(
                record.getId(),
                command.registration().toUpperCase(),
                command.templateId(),
                command.description(),
                command.scheduledDate(),
                command.estimatedHours(),
                command.component(),
                command.estimatedCost()
        ));

        eventPublisher.publishAircraftStatusChanged(new AircraftStatusChangedEvent(
                command.registration().toUpperCase(),
                AircraftStatus.AVAILABLE.name(),
                AircraftStatus.UNDER_MAINTENANCE.name(),
                record.getAircraft().getTotalFlightHours()
        ));

        return record;
    }

    @Transactional
    public MaintenanceRecord handle(StartMaintenanceCommand command) {
        return recordService.startRecord(command.recordId(), command.version());
    }

    @Transactional
    public MaintenanceRecord handle(CompleteMaintenanceCommand command) {
        MaintenanceRecord record = recordService.completeRecord(
                command.recordId(),
                command.version(),
                command.completionDetails(),
                command.actualHours(),
                command.actualCost()
        );

        eventPublisher.publishMaintenanceCompleted(new MaintenanceCompletedEvent(
                record.getId(),
                record.getAircraft().getRegistrationNumber(),
                command.actualHours(),
                command.actualCost(),
                command.completionDetails()
        ));

        eventPublisher.publishAircraftStatusChanged(new AircraftStatusChangedEvent(
                record.getAircraft().getRegistrationNumber(),
                AircraftStatus.UNDER_MAINTENANCE.name(),
                AircraftStatus.AVAILABLE.name(),
                record.getAircraft().getTotalFlightHours()
        ));

        return record;
    }

    @Transactional
    public MaintenanceRecord handle(CancelMaintenanceCommand command) {
        MaintenanceRecord record = recordService.cancelRecord(
                command.recordId(),
                command.version(),
                command.cancellationReason()
        );

        eventPublisher.publishAircraftStatusChanged(new AircraftStatusChangedEvent(
                record.getAircraft().getRegistrationNumber(),
                AircraftStatus.UNDER_MAINTENANCE.name(),
                AircraftStatus.AVAILABLE.name(),
                record.getAircraft().getTotalFlightHours()
        ));

        return record;
    }
}
