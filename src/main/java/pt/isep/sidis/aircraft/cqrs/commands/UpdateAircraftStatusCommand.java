package pt.isep.sidis.aircraft.cqrs.commands;

public record UpdateAircraftStatusCommand(
    String registration,
    String status
) {}
