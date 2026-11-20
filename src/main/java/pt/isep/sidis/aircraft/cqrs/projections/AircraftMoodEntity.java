package pt.isep.sidis.aircraft.cqrs.projections;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;

/**
 * Persistent JPA Entity for the CQRS Read Model (table aircraft_mood_views).
 * Decoupled from write aggregates (Aircraft, MaintenanceRecord).
 * Preserves mood projections across service restarts.
 */
@Entity
@Table(name = "aircraft_mood_views")
public class AircraftMoodEntity implements Serializable {

    @Id
    @Column(length = 20, nullable = false)
    private String registration;

    @Column(nullable = false)
    private String model;

    @Column(nullable = false)
    private String mood;

    @Column(nullable = false)
    private Integer energyLevel;

    @Column(nullable = false)
    private Double sparkleFactor;

    @Column(length = 500)
    private String statusMessage;

    @Column(nullable = false)
    private String operationalStatus;

    @Column(nullable = false)
    private Double totalFlightHours;

    @Column(nullable = false)
    private Instant lastUpdated;

    public AircraftMoodEntity() {}

    public AircraftMoodEntity(String registration, String model, String mood,
                              Integer energyLevel, Double sparkleFactor, String statusMessage,
                              String operationalStatus, Double totalFlightHours, Instant lastUpdated) {
        this.registration = registration;
        this.model = model;
        this.mood = mood;
        this.energyLevel = energyLevel;
        this.sparkleFactor = sparkleFactor;
        this.statusMessage = statusMessage;
        this.operationalStatus = operationalStatus;
        this.totalFlightHours = totalFlightHours != null ? totalFlightHours : 0.0;
        this.lastUpdated = lastUpdated != null ? lastUpdated : Instant.now();
    }

    public AircraftMoodView toView() {
        return new AircraftMoodView(
                registration,
                model,
                mood,
                energyLevel,
                sparkleFactor,
                statusMessage,
                operationalStatus,
                totalFlightHours,
                lastUpdated
        );
    }

    public String getRegistration() { return registration; }
    public void setRegistration(String registration) { this.registration = registration; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getMood() { return mood; }
    public void setMood(String mood) { this.mood = mood; }

    public Integer getEnergyLevel() { return energyLevel; }
    public void setEnergyLevel(Integer energyLevel) { this.energyLevel = energyLevel; }

    public Double getSparkleFactor() { return sparkleFactor; }
    public void setSparkleFactor(Double sparkleFactor) { this.sparkleFactor = sparkleFactor; }

    public String getStatusMessage() { return statusMessage; }
    public void setStatusMessage(String statusMessage) { this.statusMessage = statusMessage; }

    public String getOperationalStatus() { return operationalStatus; }
    public void setOperationalStatus(String operationalStatus) { this.operationalStatus = operationalStatus; }

    public Double getTotalFlightHours() { return totalFlightHours; }
    public void setTotalFlightHours(Double totalFlightHours) { this.totalFlightHours = totalFlightHours; }

    public Instant getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(Instant lastUpdated) { this.lastUpdated = lastUpdated; }
}
