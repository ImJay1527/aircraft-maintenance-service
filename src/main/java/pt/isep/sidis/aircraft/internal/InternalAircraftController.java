package pt.isep.sidis.aircraft.internal;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.isep.sidis.aircraft.domain.Aircraft;
import pt.isep.sidis.aircraft.replication.PeerClient;
import pt.isep.sidis.aircraft.repositories.AircraftRepository;

import java.util.Optional;

/**
 * Service-to-service endpoints (role SERVICE only, enforced in SecurityConfig).
 * Implements Unicast Peer Query Forwarding with loop prevention (X-Forwarded-From):
 *  - 200 + AircraftInfo with X-Data-Source=local if present locally
 *  - 200 + AircraftInfo with X-Data-Source=peer if resolved from a peer replica
 *  - 404 if already forwarded (stops infinite cycles) or if no replica has it
 */
@RestController
@RequestMapping("/internal/aircraft")
@Tag(name = "Internal (service-to-service & peer replication)")
public class InternalAircraftController {

    private final AircraftRepository aircraftRepository;
    private final PeerClient peerClient;

    public InternalAircraftController(AircraftRepository aircraftRepository, PeerClient peerClient) {
        this.aircraftRepository = aircraftRepository;
        this.peerClient = peerClient;
    }

    @GetMapping("/{registration}")
    public ResponseEntity<AircraftInfo> getAircraft(
            @PathVariable String registration,
            @RequestHeader(value = "X-Forwarded-From", required = false) String forwardedFrom,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            HttpServletResponse response) {

        String regUpper = registration != null ? registration.toUpperCase() : "";

        // 1. Check local database
        Optional<Aircraft> optAircraft = aircraftRepository.findByRegistrationNumber(regUpper);
        if (optAircraft.isPresent()) {
            if (response != null) {
                response.setHeader("X-Data-Source", "local");
            }
            return ResponseEntity.ok(toAircraftInfo(optAircraft.get()));
        }

        // 2. Loop prevention: if already forwarded from a peer, answer from local data only
        if (forwardedFrom != null && !forwardedFrom.isBlank()) {
            return ResponseEntity.notFound().build();
        }

        // 3. Forward query to peer replicas using unicast HTTP GET
        Optional<AircraftInfo> fromPeer = peerClient.fetchAircraftFromPeers(regUpper, requestId);
        if (fromPeer.isPresent()) {
            if (response != null) {
                response.setHeader("X-Data-Source", "peer");
            }
            return ResponseEntity.ok(fromPeer.get());
        }

        return ResponseEntity.notFound().build();
    }

    private AircraftInfo toAircraftInfo(Aircraft aircraft) {
        return new AircraftInfo(
                aircraft.getRegistrationNumber(),
                aircraft.getStatus().name(),
                aircraft.getModel().getName(),
                aircraft.getModel().getMaximumRange(),
                aircraft.getModel().getFuelCapacity(),
                aircraft.getActiveCapacity() != null ? aircraft.getActiveCapacity() : aircraft.getModel().getSeatingCapacity()
        );
    }
}
