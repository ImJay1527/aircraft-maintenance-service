package pt.isep.sidis.aircraft.cqrs.commands;

public record CompleteMaintenanceCommand(
    Long recordId,
    Long version,
    String completionDetails,
    Double actualHours,
    Double actualCost
) {}
