package pt.isep.sidis.aircraft.replication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import pt.isep.sidis.aircraft.cluster.ClusterConfig;
import pt.isep.sidis.aircraft.common.security.JwtUtils;
import pt.isep.sidis.aircraft.internal.AircraftInfo;

import java.time.Duration;
import java.util.Optional;

/**
 * HTTP implementation of PeerClient using Spring RestClient.
 * Queries peer replicas using unicast HTTP GET.
 * Includes loop-prevention header X-Forwarded-From and propagates X-Request-Id.
 */
@Component
public class HttpPeerClient implements PeerClient {

    private static final Logger log = LoggerFactory.getLogger(HttpPeerClient.class);

    private final ClusterConfig clusterConfig;
    private final JwtUtils jwtUtils;
    private final RestClient restClient;

    @Autowired
    public HttpPeerClient(ClusterConfig clusterConfig,
                          JwtUtils jwtUtils,
                          @Autowired(required = false) org.springframework.boot.ssl.SslBundles sslBundles,
                          @Value("${sidis.tls.client-bundle:}") String clientBundleName,
                          @Value("${aircraft.peer-timeout-ms:1500}") long timeoutMs) {
        this.clusterConfig = clusterConfig;
        this.jwtUtils = jwtUtils;

        java.net.http.HttpClient.Builder clientBuilder = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs));

        if (sslBundles != null && clientBundleName != null && !clientBundleName.isBlank()) {
            try {
                org.springframework.boot.ssl.SslBundle bundle = sslBundles.getBundle(clientBundleName);
                if (bundle != null) {
                    clientBuilder.sslContext(bundle.createSslContext());
                    log.info("HttpPeerClient configured with TLS bundle '{}'", clientBundleName);
                }
            } catch (Exception e) {
                log.warn("Could not load TLS client bundle '{}': {}", clientBundleName, e.getMessage());
            }
        }

        org.springframework.http.client.JdkClientHttpRequestFactory requestFactory =
                new org.springframework.http.client.JdkClientHttpRequestFactory(clientBuilder.build());
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    public HttpPeerClient(ClusterConfig clusterConfig,
                          JwtUtils jwtUtils,
                          long timeoutMs) {
        this(clusterConfig, jwtUtils, null, null, timeoutMs);
    }

    public HttpPeerClient(ClusterConfig clusterConfig, JwtUtils jwtUtils, RestClient restClient) {
        this.clusterConfig = clusterConfig;
        this.jwtUtils = jwtUtils;
        this.restClient = restClient;
    }

    @Override
    public Optional<AircraftInfo> fetchAircraftFromPeers(String registration, String requestId) {
        if (!clusterConfig.hasPeers()) {
            return Optional.empty();
        }

        String serviceToken = "Bearer " + jwtUtils.generateServiceToken(clusterConfig.getSelfName());

        for (String peerUrl : clusterConfig.getPeerUrls()) {
            try {
                log.debug("Forwarding aircraft query for {} to peer {}", registration, peerUrl);
                AircraftInfo info = restClient.get()
                        .uri(peerUrl + "/internal/aircraft/{registration}", registration)
                        .header(HttpHeaders.AUTHORIZATION, serviceToken)
                        .header("X-Forwarded-From", clusterConfig.getSelfName())
                        .header("X-Request-Id", requestId != null ? requestId : "")
                        .retrieve()
                        .body(AircraftInfo.class);

                if (info != null) {
                    log.info("Aircraft {} retrieved from peer replica {}", registration, peerUrl);
                    return Optional.of(info);
                }
            } catch (HttpClientErrorException.NotFound e) {
                log.debug("Peer {} does not hold aircraft {}", peerUrl, registration);
            } catch (Exception e) {
                log.warn("Failed to retrieve aircraft {} from peer {}: {}", registration, peerUrl, e.getMessage());
            }
        }

        return Optional.empty();
    }
}
