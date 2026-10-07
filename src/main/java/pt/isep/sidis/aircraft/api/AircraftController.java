package pt.isep.sidis.aircraft.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pt.isep.sidis.aircraft.domain.Aircraft;
import pt.isep.sidis.aircraft.services.AircraftService;

@RestController
@RequestMapping("/api/aircrafts")
public class AircraftController {

    private final AircraftService aircraftService;

    public AircraftController(AircraftService aircraftService) {
        this.aircraftService = aircraftService;
    }

    @GetMapping("/{registrationNumber}")
    public ResponseEntity<Aircraft> getAircraft(@PathVariable String registrationNumber) {
        Aircraft aircraft = aircraftService.getAircraft(registrationNumber);
        return ResponseEntity.ok(aircraft);
    }

    @PostMapping
    public ResponseEntity<Aircraft> createAircraft(@RequestBody Aircraft aircraft) {
        // Simple creation for Week 1 demonstration
        Aircraft created = aircraftService.createAircraft(aircraft);
        return ResponseEntity.ok(created);
    }
}
