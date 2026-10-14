package pt.isep.sidis.aircraft.internal;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pt.isep.sidis.aircraft.domain.Aircraft;
import pt.isep.sidis.aircraft.services.AircraftService;

/**
 * Service-to-service endpoints (role SERVICE only, enforced in SecurityConfig).
 *  - 200 + AircraftInfo if the aircraft exists (on this replica OR on a peer replica - asks peers before answering 404)
 *  - 404 if no replica has it
 */
@RestController
@RequestMapping("/internal/aircraft")
@Tag(name = "Internal (service-to-service)")
public class InternalAircraftController {

    private final AircraftService aircraftService;

    public InternalAircraftController(AircraftService aircraftService) {
        this.aircraftService = aircraftService;
    }

    @GetMapping("/{registration}")
    public ResponseEntity<AircraftInfo> getAircraft(@PathVariable String registration) {
        Aircraft aircraft = aircraftService.getAircraft(registration);
        AircraftInfo info = new AircraftInfo(
                aircraft.getRegistrationNumber(),
                aircraft.getStatus().name(),
                aircraft.getModel().getName(),
                aircraft.getModel().getMaximumRange(),
                aircraft.getModel().getFuelCapacity(),
                aircraft.getActiveCapacity() != null ? aircraft.getActiveCapacity() : aircraft.getModel().getSeatingCapacity()
        );
        return ResponseEntity.ok(info);
    }
}
