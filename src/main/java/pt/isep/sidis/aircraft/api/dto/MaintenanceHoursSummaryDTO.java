package pt.isep.sidis.aircraft.api.dto;

import pt.isep.sidis.aircraft.services.MaintenanceRecordService.MaintenanceHoursSummary;

public class MaintenanceHoursSummaryDTO {

    private String registrationNumber;
    private Double totalHours;

    public MaintenanceHoursSummaryDTO() {
    }

    public MaintenanceHoursSummaryDTO(String registrationNumber, Double totalHours) {
        this.registrationNumber = registrationNumber;
        this.totalHours = totalHours;
    }

    public MaintenanceHoursSummaryDTO(MaintenanceHoursSummary summary) {
        this.registrationNumber = summary.registrationNumber();
        this.totalHours = summary.totalHours();
    }

    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; }
    public Double getTotalHours() { return totalHours; }
    public void setTotalHours(Double totalHours) { this.totalHours = totalHours; }
}
