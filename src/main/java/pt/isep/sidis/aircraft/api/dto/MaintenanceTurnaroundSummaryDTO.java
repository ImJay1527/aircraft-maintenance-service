package pt.isep.sidis.aircraft.api.dto;

import pt.isep.sidis.aircraft.services.MaintenanceRecordService.MaintenanceTurnaroundSummary;

public class MaintenanceTurnaroundSummaryDTO {

    private String modelName;
    private Double avgDurationHours;

    public MaintenanceTurnaroundSummaryDTO() {
    }

    public MaintenanceTurnaroundSummaryDTO(String modelName, Double avgDurationHours) {
        this.modelName = modelName;
        this.avgDurationHours = avgDurationHours;
    }

    public MaintenanceTurnaroundSummaryDTO(MaintenanceTurnaroundSummary summary) {
        this.modelName = summary.modelName();
        this.avgDurationHours = summary.avgDurationHours();
    }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public Double getAvgDurationHours() { return avgDurationHours; }
    public void setAvgDurationHours(Double avgDurationHours) { this.avgDurationHours = avgDurationHours; }
}
