package pt.isep.sidis.aircraft.api.dto;

import pt.isep.sidis.aircraft.services.MaintenanceRecordService.MaintenanceCostSummary;

public class MaintenanceCostSummaryDTO {

    private String key;
    private Double estimatedCost;
    private Double actualCost;

    public MaintenanceCostSummaryDTO() {
    }

    public MaintenanceCostSummaryDTO(String key, Double estimatedCost, Double actualCost) {
        this.key = key;
        this.estimatedCost = estimatedCost;
        this.actualCost = actualCost;
    }

    public MaintenanceCostSummaryDTO(MaintenanceCostSummary summary) {
        this.key = summary.key();
        this.estimatedCost = summary.estimatedCost();
        this.actualCost = summary.actualCost();
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public Double getEstimatedCost() { return estimatedCost; }
    public void setEstimatedCost(Double estimatedCost) { this.estimatedCost = estimatedCost; }
    public Double getActualCost() { return actualCost; }
    public void setActualCost(Double actualCost) { this.actualCost = actualCost; }
}
