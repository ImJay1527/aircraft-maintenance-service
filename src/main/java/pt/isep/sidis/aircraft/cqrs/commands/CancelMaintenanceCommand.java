package pt.isep.sidis.aircraft.cqrs.commands;

public record CancelMaintenanceCommand(
    Long recordId,
    Long version,
    String cancellationReason
) {}
