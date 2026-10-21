package pt.isep.sidis.aircraft.replication;

import pt.isep.sidis.aircraft.internal.AircraftInfo;

import java.util.Optional;

/**
 * Contract for fetching aircraft data from peer replica instances.
 */
public interface PeerClient {

    Optional<AircraftInfo> fetchAircraftFromPeers(String registration, String requestId);
}
