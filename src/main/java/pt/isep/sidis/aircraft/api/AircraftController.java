package pt.isep.sidis.aircraft.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pt.isep.sidis.aircraft.cqrs.commands.RegisterAircraftCommand;
import pt.isep.sidis.aircraft.cqrs.commands.UpdateAircraftStatusCommand;
import pt.isep.sidis.aircraft.cqrs.handlers.AircraftCommandHandler;
import pt.isep.sidis.aircraft.domain.Aircraft;
import pt.isep.sidis.aircraft.services.AircraftService;

@RestController
@RequestMapping("/api/aircrafts")
public class AircraftController {

    private final AircraftService aircraftService;
    private final AircraftCommandHandler commandHandler;

    public AircraftController(AircraftService aircraftService, AircraftCommandHandler commandHandler) {
        this.aircraftService = aircraftService;
        this.commandHandler = commandHandler;
    }

    @GetMapping("/{registrationNumber}")
    public ResponseEntity<Aircraft> getAircraft(@PathVariable String registrationNumber) {
        Aircraft aircraft = aircraftService.getAircraft(registrationNumber);
        return ResponseEntity.ok(aircraft);
    }

    @PostMapping
    public ResponseEntity<Aircraft> createAircraft(@RequestBody Aircraft aircraft) {
        String modelRef = (aircraft.getModel() != null && aircraft.getModel().getName() != null && !aircraft.getModel().getName().isBlank())
                ? aircraft.getModel().getName()
                : (aircraft.getModel() != null && aircraft.getModel().getId() != null
                    ? String.valueOf(aircraft.getModel().getId())
                    : "A320neo");

        Aircraft created = commandHandler.handle(new RegisterAircraftCommand(
                aircraft.getRegistrationNumber(),
                aircraft.getRegistrationNumber(),
                modelRef
        ));
        return ResponseEntity.ok(created);
    }

    @PatchMapping("/{registrationNumber}/status")
    public ResponseEntity<Aircraft> updateStatus(
            @PathVariable String registrationNumber,
            @RequestParam String status) {
        Aircraft updated = commandHandler.handle(new UpdateAircraftStatusCommand(registrationNumber, status));
        return ResponseEntity.ok(updated);
    }
}
