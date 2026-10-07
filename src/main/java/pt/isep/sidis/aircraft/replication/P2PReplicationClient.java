package pt.isep.sidis.aircraft.replication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import pt.isep.sidis.aircraft.domain.Aircraft;

import java.util.Optional;

@Component
public class P2PReplicationClient {

    private static final Logger logger = LoggerFactory.getLogger(P2PReplicationClient.class);
    
    private final RestTemplate restTemplate;
    private final String[] peers;

    public P2PReplicationClient(@Value("${aircraft.peers:}") String peersList) {
        this.restTemplate = new RestTemplate(); // Built-in Spring HTTP client (avoids external dependencies)
        if (peersList != null && !peersList.trim().isEmpty()) {
            this.peers = peersList.split(",");
        } else {
            this.peers = new String[0];
        }
    }

    /**
     * Forwards the GET request to known peers if data is missing locally.
     * Implements Week 3 Distributed Data Consistency strategy.
     */
    public Optional<Aircraft> forwardGetAircraft(String registrationNumber) {
        if (peers.length == 0) {
            return Optional.empty();
        }

        logger.info("Local Cache Miss for Aircraft {}. Starting P2P Forwarding...", registrationNumber);

        for (String peerUrl : peers) {
            String targetUrl = peerUrl + "/api/aircrafts/" + registrationNumber;
            try {
                logger.info("Forwarding request to peer: {}", targetUrl);
                // Make HTTP GET to peer
                Aircraft aircraft = restTemplate.getForObject(targetUrl, Aircraft.class);
                if (aircraft != null) {
                    logger.info("Aircraft {} found in peer {}", registrationNumber, peerUrl);
                    return Optional.of(aircraft);
                }
            } catch (HttpClientErrorException.NotFound e) {
                // Peer doesn't have it either, ignore and try the next one
                logger.debug("Peer {} returned 404 Not Found", peerUrl);
            } catch (Exception e) {
                // Peer is unreachable or failed (Zero SPOF means we just ignore the failed node)
                logger.warn("Peer {} is unreachable or failed: {}", peerUrl, e.getMessage());
            }
        }

        logger.info("Aircraft {} not found in any available peers.", registrationNumber);
        return Optional.empty();
    }
}
