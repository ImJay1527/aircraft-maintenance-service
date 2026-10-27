package pt.isep.sidis.aircraft.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AircraftModelTest {

    @Test
    void testModelCreationAndGetters() {
        AircraftModel model = new AircraftModel("737 MAX", "Boeing", 180, 26000, 6500, 840);
        model.setId(10L);

        assertEquals(10L, model.getId());
        assertEquals("737 MAX", model.getName());
        assertEquals("737 MAX", model.getModelName());
        assertEquals("Boeing", model.getManufacturer());
        assertEquals(180, model.getSeatingCapacity());
        assertEquals(26000, model.getFuelCapacity());
        assertEquals(6500, model.getMaximumRange());
        assertEquals(840, model.getCruisingSpeed());
    }

    @Test
    void testModelSetters() {
        AircraftModel model = new AircraftModel();
        model.setId(5L);
        model.setName("A350");
        model.setModelName("A350-900");
        model.setManufacturer("Airbus");
        model.setSeatingCapacity(350);
        model.setFuelCapacity(140000);
        model.setMaximumRange(15000);
        model.setCruisingSpeed(903);

        assertEquals(5L, model.getId());
        assertEquals("A350-900", model.getName());
        assertEquals("Airbus", model.getManufacturer());
        assertEquals(350, model.getSeatingCapacity());
        assertEquals(140000, model.getFuelCapacity());
        assertEquals(15000, model.getMaximumRange());
        assertEquals(903, model.getCruisingSpeed());
    }
}
