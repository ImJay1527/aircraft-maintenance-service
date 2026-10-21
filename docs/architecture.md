# Architecture Document: Aircraft & Maintenance Service

**AISafe Flight Management System**  
*Sistemas Distribuídos (SIDIS 2026/27) – Assignment 1*

---

## 1. Executive Summary & Domain Scope

The **Aircraft & Maintenance Service** (`aircraft-maintenance-service`) is an independent, distributed microservice decomposed from the monolithic PSOFT legacy application. It has sole authority and business ownership over:
- **Aircraft Models & Technical Specifications:** Model naming, seating capacity, fuel capacity, cruise speed, and maximum range.
- **Aircraft Fleet Management:** Aircraft registration numbers, operational status (`AVAILABLE`, `IN_FLIGHT`, `UNDER_MAINTENANCE`, `INACTIVE`), and total accumulated flight hours.
- **Maintenance Templates & Checklists:** Standardized maintenance definitions (Routine A-Check, C-Check, Engine Overhaul, Cabin Retrofit) with required checklists and estimated duration.
- **Maintenance Records & Reports:** Scheduling, starting, and completing maintenance interventions, calculating turnaround times, and reporting maintenance hours and costs per aircraft.

---

## 2. Zero SPOF & Distributed Topology (SIDIS)

### 2.1 Component Cloning (Active Replication)
To eliminate Single Points of Failure (**Zero SPOF**), the service supports horizontal scaling with multiple active clone instances:
- **Instance 1:** Port `8081` (Postgres DB port `5431`)
- **Instance 2:** Port `8091` (Postgres DB port `5434`)
- **Instance 3:** Port `8101` (Postgres DB port `5437`)

Each replica maintains its own isolated database (in-memory H2 or independent PostgreSQL instance). No database is shared between replicas, ensuring that failure of an instance or its database container does not compromise the availability of the others.

### 2.2 P2P Unicast Query Forwarding
When an instance receives a query for an aircraft not stored in its local database (*cache miss*):
1. **Local Lookup:** Checks local JPA repository `AircraftRepository.findByRegistrationNumber`.
2. **Loop Prevention Check:** Inspects the incoming HTTP header `X-Forwarded-From`.
   - If present: the query already originated from another peer. The instance returns `404 Not Found` immediately, preventing ping-pong cycles and infinite loops.
3. **Peer Forwarding:** If `X-Forwarded-From` is absent, `HttpPeerClient` forwards the query to configured peers using unicast HTTP GET:
   - Header `X-Forwarded-From: <selfName>`
   - Header `Authorization: Bearer <SERVICE JWT>`
   - Header `X-Request-Id: <requestId>`
   - Connect and read timeout: `1500ms`.
4. **Provenance Tracking:** Responses carry the `X-Data-Source` header:
   - `X-Data-Source: local` – answered directly from local storage.
   - `X-Data-Source: peer` – aggregated and returned via peer clone.

### 2.3 CAP Theorem Trade-off Analysis
- **Classification:** **AP (Availability and Partition Tolerance)** with eventual consistency.
- **Availability:** Replicas answer locally or via peers even if some nodes are partitioned or offline.
- **Partition Tolerance:** Network splits allow each partition to continue serving local reads and writes.
- **Consistency:** Eventual consistency is accepted across replicas; temporary disconnects fail gracefully rather than locking the entire system.

---

## 3. Security & Inter-Service Authentication

1. **Tokens & Secrets:**
   - Stateless JWT tokens (HS256) signed with a shared cluster secret (`JWT_SECRET`).
2. **Inter-Service Communication (`/internal/**`):**
   - Strictly reserved for role `SERVICE`.
   - Service tokens are generated on-the-fly with `JwtUtils.generateServiceToken("aircraft-maintenance-service")`.
   - Normal user tokens receive `403 Forbidden`.
3. **Public APIs (`/api/**`):**
   - Secured via Spring Method Security (`@PreAuthorize`) and Role-Based Access Control:
     - `AIRCRAFT_MECHANIC`, `MAINTENANCE_TECHNICIAN`: manage aircraft and records.
     - `MAINTENANCE_SUPERVISOR`: review checklists and reports.
     - `ADMIN`: full system access.
4. **Audit Logging:**
   - `AuditLogFilter` logs every HTTP request with client IP, authenticated user, assigned roles, HTTP method, URI, and resulting HTTP status code.

---

## 4. Service Contract (`/internal/aircraft/{registration}`)

The contract with `flight-operations-service` requires exact JSON field compatibility:

```json
{
  "registrationNumber": "CS-TPA",
  "status": "AVAILABLE",
  "modelName": "A320neo",
  "maxRange": 6300.0,
  "fuelCapacity": 24000.0,
  "activeCapacity": 160
}
```

- Status codes:
  - `200 OK`: aircraft exists on this replica or was retrieved from a peer.
  - `404 Not Found`: aircraft does not exist on any reachable replica.
  - `401 / 403`: unauthorized access.

---

## 5. Verification & Test Evidence

| Layer | Test Suite | Scope | Result |
|---|---|---|---|
| **Unit & Integration** | `PeerForwardingTests` & `AircraftMaintenanceApplicationTests` | 14 test cases: Local hits, peer forwarding, loop prevention, 404 handling, security RBAC | **14/14 PASS** |
| **API End-to-End** | Postman / Newman (`aircraft-maintenance.postman_collection.json`) | 15 requests, 29 assertions: Smoke, Actuator, Service Contract, Forwarding, Maintenance APIs | **15/15 PASS** |
| **System-wide Cross-Component** | Flight Operations `10 Whole system` suite | Cross-service REST validation between live instances on ports 8081, 8082, 8083 | **Verified Healthy** |
