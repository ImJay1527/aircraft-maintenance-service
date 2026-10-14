package pt.isep.sidis.aircraft.domain;

import jakarta.persistence.*;

import java.time.LocalDate;

/**
 * Entity representing a specific Aircraft instance.
 * Mirrors the PSOFT domain requirements (US102).
 */
@Entity
@Table(name = "aircrafts")
public class Aircraft {

    @Id
    @Column(name = "registration_number", length = 20, nullable = false, unique = true)
    private String registrationNumber;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "model_id", nullable = false)
    private AircraftModel model;

    @Column(nullable = false)
    private LocalDate manufacturingDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AircraftStatus status;

    @Column(nullable = false)
    private String activeConfigurationName = "Economy";

    @Column(nullable = false)
    private Integer activeCapacity = 160;

    @Column(nullable = false)
    private Double totalFlightHours = 0.0;

    @Column(nullable = false)
    private Integer numberOfAssignments = 0;

    @Version
    private Long version;

    public Aircraft() {
    }

    public Aircraft(String registrationNumber, AircraftModel model, LocalDate manufacturingDate, AircraftStatus status) {
        this(registrationNumber, model, manufacturingDate, status, "Economy", model != null ? model.getSeatingCapacity() : 160);
    }

    public Aircraft(String registrationNumber, AircraftModel model, LocalDate manufacturingDate, AircraftStatus status, String activeConfigurationName, Integer activeCapacity) {
        this.registrationNumber = registrationNumber != null ? registrationNumber.toUpperCase() : null;
        this.model = model;
        this.manufacturingDate = manufacturingDate;
        this.status = status;
        this.activeConfigurationName = activeConfigurationName != null ? activeConfigurationName : "Economy";
        this.activeCapacity = activeCapacity != null ? activeCapacity : (model != null ? model.getSeatingCapacity() : 160);
    }

    public void addFlightHours(Double hours) {
        if (hours != null && hours > 0) {
            this.totalFlightHours += hours;
        }
    }

    public void addAssignment() {
        this.numberOfAssignments++;
    }

    public void updateStatus(AircraftStatus newStatus) {
        if (newStatus != null) {
            this.status = newStatus;
        }
    }

    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; }
    public AircraftModel getModel() { return model; }
    public void setModel(AircraftModel model) { this.model = model; }
    public LocalDate getManufacturingDate() { return manufacturingDate; }
    public void setManufacturingDate(LocalDate manufacturingDate) { this.manufacturingDate = manufacturingDate; }
    public AircraftStatus getStatus() { return status; }
    public void setStatus(AircraftStatus status) { this.status = status; }
    public String getActiveConfigurationName() { return activeConfigurationName; }
    public void setActiveConfigurationName(String activeConfigurationName) { this.activeConfigurationName = activeConfigurationName; }
    public Integer getActiveCapacity() { return activeCapacity; }
    public void setActiveCapacity(Integer activeCapacity) { this.activeCapacity = activeCapacity; }
    public Double getTotalFlightHours() { return totalFlightHours; }
    public void setTotalFlightHours(Double totalFlightHours) { this.totalFlightHours = totalFlightHours; }
    public Integer getNumberOfAssignments() { return numberOfAssignments; }
    public void setNumberOfAssignments(Integer numberOfAssignments) { this.numberOfAssignments = numberOfAssignments; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
