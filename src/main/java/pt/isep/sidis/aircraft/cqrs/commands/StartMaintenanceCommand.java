package pt.isep.sidis.aircraft.cqrs.commands;

public record StartMaintenanceCommand(
    Long recordId,
    Long version
) {}
