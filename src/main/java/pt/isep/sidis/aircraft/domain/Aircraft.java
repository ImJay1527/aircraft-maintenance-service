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

    public Aircraft() {
    }

    public Aircraft(String registrationNumber, AircraftModel model, LocalDate manufacturingDate, AircraftStatus status) {
        this.registrationNumber = registrationNumber;
        this.model = model;
        this.manufacturingDate = manufacturingDate;
        this.status = status;
    }

    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; }
    public AircraftModel getModel() { return model; }
    public void setModel(AircraftModel model) { this.model = model; }
    public LocalDate getManufacturingDate() { return manufacturingDate; }
    public void setManufacturingDate(LocalDate manufacturingDate) { this.manufacturingDate = manufacturingDate; }
    public AircraftStatus getStatus() { return status; }
    public void setStatus(AircraftStatus status) { this.status = status; }
}
