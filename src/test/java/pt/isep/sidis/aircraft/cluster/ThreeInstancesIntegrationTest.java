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
 * End-to-end 3-instance cluster integration test (PL3 "test with 3+ instances").
 * Boots three real Spring Boot instances on dynamic ports, each with an independent in-memory database.
 * Validates:
 *   1. Local data access on Instance 3 (CS-TPZ partitioned on instance 3)
 *   2. Forwarding from Instance 1 to Instance 3
 *   3. Forwarding from Instance 2 to Instance 3
 *   4. Reverse forwarding from Instance 3 to Instance 1 (CS-TPY)
 *   5. Distributed loop prevention across 3 nodes
 *   6. Zero SPOF: when Instance 2 crashes, Instance 1 and Instance 3 continue communicating seamlessly
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ThreeInstancesIntegrationTest {

    private static ConfigurableApplicationContext instance1;
    private static ConfigurableApplicationContext instance2;
    private static ConfigurableApplicationContext instance3;
    private static String url1;
    private static String url2;
    private static String url3;
    private static String serviceToken;

    private static final RestClient http = RestClient.builder()
            .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> { })
            .build();

    @BeforeAll
    static void startCluster() throws IOException {
        int port1 = freePort();
        int port2 = freePort();
        int port3 = freePort();
        url1 = "http://localhost:" + port1;
        url2 = "http://localhost:" + port2;
        url3 = "http://localhost:" + port3;

        instance1 = new SpringApplicationBuilder(AircraftMaintenanceApplication.class)
                .profiles("instance1")
                .run(
                        "--server.port=" + port1,
                        "--aircraft.instance-name=instance1",
                        "--aircraft.db.name=three_cluster_db_1",
                        "--aircraft.peers=" + url2 + "," + url3,
                        "--aircraft.peer-timeout-ms=2500"
                );

        instance2 = new SpringApplicationBuilder(AircraftMaintenanceApplication.class)
                .profiles("instance2")
                .run(
                        "--server.port=" + port2,
                        "--aircraft.instance-name=instance2",
                        "--aircraft.db.name=three_cluster_db_2",
                        "--aircraft.peers=" + url1 + "," + url3,
                        "--aircraft.peer-timeout-ms=2500"
                );

        instance3 = new SpringApplicationBuilder(AircraftMaintenanceApplication.class)
                .profiles("instance3")
                .run(
                        "--server.port=" + port3,
                        "--aircraft.instance-name=instance3",
                        "--aircraft.db.name=three_cluster_db_3",
                        "--aircraft.peers=" + url1 + "," + url2,
                        "--aircraft.peer-timeout-ms=2500"
                );

        JwtUtils jwtUtils = instance1.getBean(JwtUtils.class);
        serviceToken = jwtUtils.generateServiceToken("three-cluster-runner");
    }

    @AfterAll
    static void shutdownCluster() {
        if (instance1 != null && instance1.isActive()) instance1.close();
        if (instance2 != null && instance2.isActive()) instance2.close();
        if (instance3 != null && instance3.isActive()) instance3.close();
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }

    @Test
    @Order(1)
    void dataPartitionedOnInstance3IsLocalOnInstance3() {
        ResponseEntity<AircraftInfo> res = http.get()
                .uri(url3 + "/internal/aircraft/CS-TPZ")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
                .retrieve()
                .toEntity(AircraftInfo.class);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().registrationNumber()).isEqualTo("CS-TPZ");
        assertThat(res.getHeaders().getFirst("X-Data-Source")).isEqualTo("local");
        assertThat(res.getHeaders().getFirst("X-Instance")).isEqualTo("instance3");
    }

    @Test
    @Order(2)
    void dataPartitionedOnInstance3IsForwardedThroughInstance1() {
        ResponseEntity<AircraftInfo> res = http.get()
                .uri(url1 + "/internal/aircraft/CS-TPZ")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
                .retrieve()
                .toEntity(AircraftInfo.class);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().registrationNumber()).isEqualTo("CS-TPZ");
        assertThat(res.getHeaders().getFirst("X-Data-Source")).isEqualTo("peer");
        assertThat(res.getHeaders().getFirst("X-Instance")).isEqualTo("instance1");
    }

    @Test
    @Order(3)
    void dataPartitionedOnInstance3IsForwardedThroughInstance2() {
        ResponseEntity<AircraftInfo> res = http.get()
                .uri(url2 + "/internal/aircraft/CS-TPZ")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
                .retrieve()
                .toEntity(AircraftInfo.class);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().registrationNumber()).isEqualTo("CS-TPZ");
        assertThat(res.getHeaders().getFirst("X-Data-Source")).isEqualTo("peer");
        assertThat(res.getHeaders().getFirst("X-Instance")).isEqualTo("instance2");
    }

    @Test
    @Order(4)
    void dataPartitionedOnInstance1IsForwardedThroughInstance3() {
        ResponseEntity<AircraftInfo> res = http.get()
                .uri(url3 + "/internal/aircraft/CS-TPY")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
                .retrieve()
                .toEntity(AircraftInfo.class);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().registrationNumber()).isEqualTo("CS-TPY");
        assertThat(res.getHeaders().getFirst("X-Data-Source")).isEqualTo("peer");
        assertThat(res.getHeaders().getFirst("X-Instance")).isEqualTo("instance3");
    }

    @Test
    @Order(5)
    void multiNodeLoopPreventionStopsPingPongStorm() {
        ResponseEntity<String> res = http.get()
                .uri(url1 + "/internal/aircraft/CS-UNKNOWN")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
                .header("X-Forwarded-From", "instance2")
                .retrieve()
                .toEntity(String.class);

        assertThat(res.getStatusCode().value()).isEqualTo(404);
        assertThat(res.getHeaders().getFirst("X-Instance")).isEqualTo("instance1");
    }

    @Test
    @Order(6)
    void zeroSpofWhenInstance2CrashesInstance1AndInstance3StillCommunicate() {
        // Simulate catastrophic failure of instance2
        instance2.close();

        // 1. Instance 1 can still query Instance 3
        ResponseEntity<AircraftInfo> res1to3 = http.get()
                .uri(url1 + "/internal/aircraft/CS-TPZ")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
                .retrieve()
                .toEntity(AircraftInfo.class);

        assertThat(res1to3.getStatusCode().value()).isEqualTo(200);
        assertThat(res1to3.getBody()).isNotNull();
        assertThat(res1to3.getBody().registrationNumber()).isEqualTo("CS-TPZ");
        assertThat(res1to3.getHeaders().getFirst("X-Data-Source")).isEqualTo("peer");

        // 2. Instance 3 can still query Instance 1
        ResponseEntity<AircraftInfo> res3to1 = http.get()
                .uri(url3 + "/internal/aircraft/CS-TPY")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
                .retrieve()
                .toEntity(AircraftInfo.class);

        assertThat(res3to1.getStatusCode().value()).isEqualTo(200);
        assertThat(res3to1.getBody()).isNotNull();
        assertThat(res3to1.getBody().registrationNumber()).isEqualTo("CS-TPY");
        assertThat(res3to1.getHeaders().getFirst("X-Data-Source")).isEqualTo("peer");

        // 3. Querying data that only existed on crashed instance2 returns 404 gracefully
        ResponseEntity<String> resDead = http.get()
                .uri(url1 + "/internal/aircraft/CS-TPX")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
                .retrieve()
                .toEntity(String.class);

        assertThat(resDead.getStatusCode().value()).isEqualTo(404);
    }
}
