package pt.isep.sidis.aircraft.replication;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import pt.isep.sidis.aircraft.cluster.ClusterConfig;
import pt.isep.sidis.aircraft.common.security.JwtUtils;
import pt.isep.sidis.aircraft.internal.AircraftInfo;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HttpPeerClientTest {

    @Mock
    private ClusterConfig clusterConfig;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private RestClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private HttpPeerClient peerClient;

    @BeforeEach
    void setUp() {
        peerClient = new HttpPeerClient(clusterConfig, jwtUtils, restClient);
    }

    @Test
    void whenNoPeersConfiguredReturnsEmptyImmediately() {
        when(clusterConfig.hasPeers()).thenReturn(false);

        Optional<AircraftInfo> result = peerClient.fetchAircraftFromPeers("CS-TPA", "trace-1");

        assertThat(result).isEmpty();
        verifyNoInteractions(restClient);
    }

    @Test
    void whenPeerReturnsAircraftReturnsPresentOptional() {
        when(clusterConfig.hasPeers()).thenReturn(true);
        when(clusterConfig.getSelfName()).thenReturn("instance1");
        when(clusterConfig.getPeerUrls()).thenReturn(List.of("http://localhost:8082"));
        when(jwtUtils.generateServiceToken("instance1")).thenReturn("dummy-service-token");

        AircraftInfo expected = new AircraftInfo("CS-TPX", "AVAILABLE", "A320neo", 6300.0, 24000.0, 160);

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(eq("http://localhost:8082/internal/aircraft/{registration}"), eq("CS-TPX")))
                .thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(eq(HttpHeaders.AUTHORIZATION), eq("Bearer dummy-service-token")))
                .thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(eq("X-Forwarded-From"), eq("instance1")))
                .thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(eq("X-Request-Id"), eq("req-123")))
                .thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(AircraftInfo.class)).thenReturn(expected);

        Optional<AircraftInfo> result = peerClient.fetchAircraftFromPeers("CS-TPX", "req-123");

        assertThat(result).isPresent();
        assertThat(result.get().registrationNumber()).isEqualTo("CS-TPX");
        assertThat(result.get().modelName()).isEqualTo("A320neo");
    }

    @Test
    void whenPeerReturns404NotFoundReturnsEmpty() {
        when(clusterConfig.hasPeers()).thenReturn(true);
        when(clusterConfig.getSelfName()).thenReturn("instance1");
        when(clusterConfig.getPeerUrls()).thenReturn(List.of("http://localhost:8082"));
        when(jwtUtils.generateServiceToken("instance1")).thenReturn("dummy-token");

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), eq("CS-UNKNOWN"))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(AircraftInfo.class)).thenThrow(HttpClientErrorException.NotFound.create(
                org.springframework.http.HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null));

        Optional<AircraftInfo> result = peerClient.fetchAircraftFromPeers("CS-UNKNOWN", "trace-2");

        assertThat(result).isEmpty();
    }

    @Test
    void whenPeerFailsWithConnectionErrorReturnsEmptyGracefully() {
        when(clusterConfig.hasPeers()).thenReturn(true);
        when(clusterConfig.getSelfName()).thenReturn("instance1");
        when(clusterConfig.getPeerUrls()).thenReturn(List.of("http://unreachable-host:9999"));
        when(jwtUtils.generateServiceToken("instance1")).thenReturn("dummy-token");

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(AircraftInfo.class)).thenThrow(new ResourceAccessException("Connection refused"));

        Optional<AircraftInfo> result = peerClient.fetchAircraftFromPeers("CS-TPA", null);

        assertThat(result).isEmpty();
    }

    @Test
    void whenFirstPeerFailsAndSecondPeerSucceeds() {
        when(clusterConfig.hasPeers()).thenReturn(true);
        when(clusterConfig.getSelfName()).thenReturn("instance1");
        when(clusterConfig.getPeerUrls()).thenReturn(List.of("http://peer1:8081", "http://peer2:8082"));
        when(jwtUtils.generateServiceToken("instance1")).thenReturn("dummy-token");

        AircraftInfo expected = new AircraftInfo("CS-TPX", "AVAILABLE", "A320neo", 6300.0, 24000.0, 160);

        RestClient.RequestHeadersUriSpec uriSpec1 = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec headersSpec1 = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec respSpec1 = mock(RestClient.ResponseSpec.class);

        RestClient.RequestHeadersUriSpec uriSpec2 = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec headersSpec2 = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec respSpec2 = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn(uriSpec1, uriSpec2);

        when(uriSpec1.uri(eq("http://peer1:8081/internal/aircraft/{registration}"), eq("CS-TPX"))).thenReturn(headersSpec1);
        when(headersSpec1.header(anyString(), anyString())).thenReturn(headersSpec1);
        when(headersSpec1.retrieve()).thenReturn(respSpec1);
        when(respSpec1.body(AircraftInfo.class)).thenThrow(new ResourceAccessException("Peer 1 down"));

        when(uriSpec2.uri(eq("http://peer2:8082/internal/aircraft/{registration}"), eq("CS-TPX"))).thenReturn(headersSpec2);
        when(headersSpec2.header(anyString(), anyString())).thenReturn(headersSpec2);
        when(headersSpec2.retrieve()).thenReturn(respSpec2);
        when(respSpec2.body(AircraftInfo.class)).thenReturn(expected);

        Optional<AircraftInfo> result = peerClient.fetchAircraftFromPeers("CS-TPX", "trace-3");

        assertThat(result).isPresent();
        assertThat(result.get().registrationNumber()).isEqualTo("CS-TPX");
    }
}
