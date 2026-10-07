package pt.isep.sidis.aircraft.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity representing an Aircraft Model.
 * Mirrors the PSOFT domain requirements (US101).
 */
@Entity
@Table(name = "aircraft_models")
@Getter
@Setter
@NoArgsConstructor
public class AircraftModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String manufacturer;

    @Column(nullable = false)
    private int seatingCapacity;

    @Column(nullable = false)
    private int fuelCapacity;

    @Column(nullable = false)
    private int maximumRange;

    @Column(nullable = false)
    private int cruisingSpeed;

    public AircraftModel(String name, String manufacturer, int seatingCapacity, int fuelCapacity, int maximumRange, int cruisingSpeed) {
        this.name = name;
        this.manufacturer = manufacturer;
        this.seatingCapacity = seatingCapacity;
        this.fuelCapacity = fuelCapacity;
        this.maximumRange = maximumRange;
        this.cruisingSpeed = cruisingSpeed;
    }
}
