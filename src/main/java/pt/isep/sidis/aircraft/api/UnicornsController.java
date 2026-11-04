package pt.isep.sidis.aircraft.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pt.isep.sidis.aircraft.cqrs.handlers.AircraftQueryHandler;
import pt.isep.sidis.aircraft.cqrs.projections.AircraftMoodView;
import pt.isep.sidis.aircraft.cqrs.queries.GetAircraftMoodQuery;

import java.util.List;

/**
 * Mandatory Mock Endpoint for Assignment 2.
 * Returns the mood status of airplanes in JSON format.
 * Integrates directly with the CQRS pattern as an external REST interface
 * to a dedicated, read-optimized projection (AircraftMoodProjection).
 */
@RestController
@RequestMapping("/api/v2/unicorns")
@Tag(name = "Unicorns & Aircraft Mood", description = "CQRS Read-Side query endpoint returning aircraft mood status")
public class UnicornsController {

    private final AircraftQueryHandler queryHandler;

    public UnicornsController(AircraftQueryHandler queryHandler) {
        this.queryHandler = queryHandler;
    }

    @GetMapping
    @Operation(summary = "Get the mood status of all airplanes (CQRS Query)")
    public ResponseEntity<List<AircraftMoodView>> getAirplanesMood(
            @RequestParam(required = false) String registration) {

        GetAircraftMoodQuery query = (registration != null && !registration.isBlank())
                ? GetAircraftMoodQuery.forRegistration(registration)
                : GetAircraftMoodQuery.all();

        List<AircraftMoodView> moods = queryHandler.handle(query);
        return ResponseEntity.ok(moods);
    }

    @GetMapping("/{registration}")
    @Operation(summary = "Get the mood status of a specific airplane by registration (CQRS Query)")
    public ResponseEntity<AircraftMoodView> getAirplaneMoodByRegistration(
            @PathVariable String registration) {

        List<AircraftMoodView> moods = queryHandler.handle(GetAircraftMoodQuery.forRegistration(registration));
        if (moods.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(moods.get(0));
    }
}
