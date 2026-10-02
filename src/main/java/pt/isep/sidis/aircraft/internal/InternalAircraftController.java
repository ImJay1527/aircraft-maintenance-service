package pt.isep.sidis.aircraft.internal;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service endpoints (role SERVICE only, enforced in SecurityConfig).
 *
 * TODO(owner): implement with the real AircraftRepository once aircraftmanagement is ported.
 *  - 200 + AircraftInfo if the aircraft exists (on this replica OR on a peer replica - ask peers before answering 404)
 *  - 404 if no replica has it
 */
@RestController
@RequestMapping("/internal/aircraft")
@Tag(name = "Internal (service-to-service)")
public class InternalAircraftController {

    @GetMapping("/{registration}")
    public ResponseEntity<AircraftInfo> getAircraft(@PathVariable String registration) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
