package pt.isep.sidis.aircraft.cluster;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import pt.isep.sidis.aircraft.AircraftMaintenanceApplication;
import pt.isep.sidis.aircraft.common.security.JwtUtils;
import pt.isep.sidis.aircraft.internal.AircraftInfo;

import java.io.IOException;
import java.net.ServerSocket;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end multi-instance cluster integration test.
 * Boots two real Spring Boot instances on dynamic ports, each with an independent in-memory database.
 * Validates:
 *   1. Local data access on Instance 1 (X-Data-Source: local)
 *   2. P2P query forwarding from Instance 1 to Instance 2 (X-Data-Source: peer)
 *   3. P2P query forwarding from Instance 2 to Instance 1 (X-Data-Source: peer)
 *   4. Loop prevention / cycle breaking using X-Forwarded-From
 *   5. Distributed tracing propagation (X-Request-Id, X-Instance)
 *   6. Fault tolerance / resilience: closing Instance 2 leaves Instance 1 operating locally with 0 downtime
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TwoInstancesIntegrationTest {

    private static ConfigurableApplicationContext instance1;
    private static ConfigurableApplicationContext instance2;
    private static String url1;
    private static String url2;
    private static String serviceToken;
    private static String userToken;

    private static final RestClient http = RestClient.builder()
            .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> { }) // Allow asserting status codes
            .build();

    @BeforeAll
    static void startCluster() throws IOException {
        int port1 = freePort();
        int port2 = freePort();
        url1 = "http://localhost:" + port1;
        url2 = "http://localhost:" + port2;

        instance1 = new SpringApplicationBuilder(AircraftMaintenanceApplication.class)
                .profiles("instance1")
                .run(
                        "--server.port=" + port1,
                        "--aircraft.instance-name=instance1",
                        "--aircraft.db.name=cluster_db_1",
                        "--aircraft.peers=" + url2,
                        "--aircraft.peer-timeout-ms=2500"
                );

        instance2 = new SpringApplicationBuilder(AircraftMaintenanceApplication.class)
                .profiles("instance2")
                .run(
                        "--server.port=" + port2,
                        "--aircraft.instance-name=instance2",
                        "--aircraft.db.name=cluster_db_2",
                        "--aircraft.peers=" + url1,
                        "--aircraft.peer-timeout-ms=2500"
                );

        JwtUtils jwtUtils = instance1.getBean(JwtUtils.class);
        serviceToken = jwtUtils.generateServiceToken("cluster-test-runner");
        userToken = jwtUtils.generateToken("cluster-user", "ATCC");
    }

    @AfterAll
    static void shutdownCluster() {
        if (instance1 != null && instance1.isActive()) {
            instance1.close();
        }
        if (instance2 != null && instance2.isActive()) {
            instance2.close();
        }
    }

    @Test
    @Order(1)
    void localDataAccessDoesNotAskPeers() {
        // CS-TPA is present in the standard bootstrap for all instances
        ResponseEntity<AircraftInfo> res = http.get()
                .uri(url1 + "/internal/aircraft/CS-TPA")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
                .retrieve()
                .toEntity(AircraftInfo.class);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().registrationNumber()).isEqualTo("CS-TPA");
        assertThat(res.getHeaders().getFirst("X-Data-Source")).isEqualTo("local");
        assertThat(res.getHeaders().getFirst("X-Instance")).isEqualTo("instance1");
    }

    @Test
    @Order(2)
    void dataPartitionedOnInstance1IsLocalOnInstance1() {
        // CS-TPY is bootstrapped strictly on instance1
        ResponseEntity<AircraftInfo> res = http.get()
                .uri(url1 + "/internal/aircraft/CS-TPY")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
                .retrieve()
                .toEntity(AircraftInfo.class);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().registrationNumber()).isEqualTo("CS-TPY");
        assertThat(res.getHeaders().getFirst("X-Data-Source")).isEqualTo("local");
        assertThat(res.getHeaders().getFirst("X-Instance")).isEqualTo("instance1");
    }

    @Test
    @Order(3)
    void dataPartitionedOnInstance2IsForwardedThroughInstance1() {
        // CS-TPX is bootstrapped strictly on instance2
        ResponseEntity<AircraftInfo> res = http.get()
                .uri(url1 + "/internal/aircraft/CS-TPX")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
                .header("X-Request-Id", "trace-fwd-01")
                .retrieve()
                .toEntity(AircraftInfo.class);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().registrationNumber()).isEqualTo("CS-TPX");
        assertThat(res.getBody().modelName()).isEqualTo("A320neo");
        assertThat(res.getHeaders().getFirst("X-Data-Source")).isEqualTo("peer");
        assertThat(res.getHeaders().getFirst("X-Instance")).isEqualTo("instance1");
        assertThat(res.getHeaders().getFirst("X-Request-Id")).isEqualTo("trace-fwd-01");
    }

    @Test
    @Order(4)
    void dataPartitionedOnInstance1IsForwardedThroughInstance2() {
        // CS-TPY is on instance1; requesting via instance2 forwards query to instance1
        ResponseEntity<AircraftInfo> res = http.get()
                .uri(url2 + "/internal/aircraft/CS-TPY")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
                .retrieve()
                .toEntity(AircraftInfo.class);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().registrationNumber()).isEqualTo("CS-TPY");
        assertThat(res.getHeaders().getFirst("X-Data-Source")).isEqualTo("peer");
        assertThat(res.getHeaders().getFirst("X-Instance")).isEqualTo("instance2");
    }

    @Test
    @Order(5)
    void unknownAircraftReturns404AfterPeerSearchFails() {
        ResponseEntity<Void> res = http.get()
                .uri(url1 + "/internal/aircraft/CS-NONEXISTENT")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
                .retrieve()
                .toBodilessEntity();

        assertThat(res.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    @Order(6)
    void loopPreventionStopsImmediatelyWhenForwardedHeaderPresent() {
        // Simulates receiving a forwarded request: must NOT forward again to peer
        ResponseEntity<Void> res = http.get()
                .uri(url1 + "/internal/aircraft/CS-UNKNOWN")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
                .header("X-Forwarded-From", "instance2")
                .retrieve()
                .toBodilessEntity();

        assertThat(res.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    @Order(7)
    void userTokenCannotAccessInternalEndpoint() {
        ResponseEntity<Void> res = http.get()
                .uri(url1 + "/internal/aircraft/CS-TPA")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                .retrieve()
                .toBodilessEntity();

        assertThat(res.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    @Order(8)
    void unauthenticatedAccessIsRejectedWith401() {
        ResponseEntity<Void> res = http.get()
                .uri(url1 + "/internal/aircraft/CS-TPA")
                .retrieve()
                .toBodilessEntity();

        assertThat(res.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    @Order(9)
    void withInstance2DownInstance1MaintainsAvailabilityForLocalData() {
        // Simulate Instance 2 crash / failure
        instance2.close();

        // Instance 1 local data remains 100% available with 0 downtime
        ResponseEntity<AircraftInfo> localRes = http.get()
                .uri(url1 + "/internal/aircraft/CS-TPA")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
                .retrieve()
                .toEntity(AircraftInfo.class);

        assertThat(localRes.getStatusCode().value()).isEqualTo(200);
        assertThat(localRes.getHeaders().getFirst("X-Data-Source")).isEqualTo("local");

        // Remote aircraft query fails gracefully with 404 (no crash, no 500 error)
        ResponseEntity<Void> remoteRes = http.get()
                .uri(url1 + "/internal/aircraft/CS-TPX")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
                .retrieve()
                .toBodilessEntity();

        assertThat(remoteRes.getStatusCode().value()).isEqualTo(404);
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
