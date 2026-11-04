package pt.isep.sidis.aircraft.cqrs.commands;

public record RegisterAircraftCommand(
    String registration,
    String serialNumber,
    String modelCode
) {}
