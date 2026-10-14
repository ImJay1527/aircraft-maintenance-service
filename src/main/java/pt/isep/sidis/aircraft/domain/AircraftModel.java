package pt.isep.sidis.aircraft.domain;

import jakarta.persistence.*;

/**
 * Entity representing an Aircraft Model.
 * Mirrors the PSOFT domain requirements (US101).
 */
@Entity
@Table(name = "aircraft_models")
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

    public AircraftModel() {
    }

    public AircraftModel(String name, String manufacturer, int seatingCapacity, int fuelCapacity, int maximumRange, int cruisingSpeed) {
        this.name = name;
        this.manufacturer = manufacturer;
        this.seatingCapacity = seatingCapacity;
        this.fuelCapacity = fuelCapacity;
        this.maximumRange = maximumRange;
        this.cruisingSpeed = cruisingSpeed;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getModelName() { return name; }
    public void setModelName(String modelName) { this.name = modelName; }
    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }
    public int getSeatingCapacity() { return seatingCapacity; }
    public void setSeatingCapacity(int seatingCapacity) { this.seatingCapacity = seatingCapacity; }
    public int getFuelCapacity() { return fuelCapacity; }
    public void setFuelCapacity(int fuelCapacity) { this.fuelCapacity = fuelCapacity; }
    public int getMaximumRange() { return maximumRange; }
    public void setMaximumRange(int maximumRange) { this.maximumRange = maximumRange; }
    public int getCruisingSpeed() { return cruisingSpeed; }
    public void setCruisingSpeed(int cruisingSpeed) { this.cruisingSpeed = cruisingSpeed; }
}
