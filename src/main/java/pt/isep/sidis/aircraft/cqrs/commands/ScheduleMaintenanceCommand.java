package pt.isep.sidis.aircraft.cqrs.commands;

import java.time.LocalDate;

public record ScheduleMaintenanceCommand(
    String registration,
    Long templateId,
    String description,
    LocalDate scheduledDate,
    Double estimatedHours,
    String component,
    Double estimatedCost
) {}
