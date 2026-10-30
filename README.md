# Aircraft & Maintenance Service (`aircraft-maintenance-service`)

**AISafe Flight Management System** – Distributed Microservice Architecture  
*Sistemas Distribuídos (LETI-SIDIS 2026/2027) – Assignment 1*  
**Author**: André (1240567)  
**Professor**: Orlando Sousa  

---

## 1. Domain Scope & Bounded Context

Decomposed from the monolithic PSOFT legacy application using **Domain-Driven Design (DDD)** principles. This microservice has sole bounded context authority and lifecycle ownership over:
* **Aircraft Models & Specifications:** Model identification, seating capacity, fuel capacity, maximum operational range, and cruise speed.
* **Fleet Inventory & Airworthiness:** Physical aircraft instances, registration numbers, flight hours accumulation, and operational availability status (`AVAILABLE`, `IN_FLIGHT`, `UNDER_MAINTENANCE`, `INACTIVE`).
* **Maintenance Templates & Checklists:** Standardized inspection protocols (Routine A-Check, C-Check, Engine Overhaul, WiFi Retrofit) and dynamic checklists.
* **Maintenance Interventions & Analytics:** Work order execution (`create` $\rightarrow$ `start` $\rightarrow$ `complete`), turnaround times, and aggregated maintenance hour/cost analytics per aircraft.

---

## 2. Distributed Systems Architecture & Theoretical Foundations

The system architecture is grounded in foundational distributed systems theory:

1. **Active Component Cloning (Zero SPOF):**
   * Eliminates Single Points of Failure through active multi-instance replication (`aircraft-1`, `aircraft-2`, `aircraft-3`).
   * Adheres strictly to the **Database-per-Service** pattern: each instance is backed by its own isolated database schema (H2 or dedicated PostgreSQL container), ensuring that failure of an instance or its database container does not compromise the availability of surviving replicas.
2. **CAP Theorem (AP) & PACELC (PA/EL):**
   * Prioritizes **Availability** and **Partition Tolerance** ($AP$) over immediate linearizable consistency, as air traffic queries must remain non-blocking even during network degradation.
   * Under non-partitioned operation, prioritizes **Latency** ($EL$) by serving local records with zero network hops.
3. **P2P Unicast Query Forwarding:**
   * When an instance encounters a local cache miss, it queries peer replicas using unicast HTTP GET with bounded connect/read timeouts (1500 ms).
4. **Distributed Loop Prevention ($hop \le 1$):**
   * Routing storms and ping-pong cycles are strictly prevented via the `X-Forwarded-From` header. Any query received with this header restricts lookups to local data only, returning `404 Not Found` if not present and never forwarding further.
5. **Distributed Tracing & Provenance:**
   * Correlation ID (`X-Request-Id`) propagated across all inter-service and peer requests.
   * Lineage headers `X-Instance` (answering node) and `X-Data-Source: local | peer` declare execution provenance.
   * Injected into SLF4J **Mapped Diagnostic Context (MDC)** for centralized log aggregation.
6. **Zero-Trust Security & RBAC:**
   * Cryptographically signed stateless JWT (HMAC-SHA256).
   * Inter-service boundary (`/internal/**`) strictly requires `ROLE_SERVICE`. End-user tokens receive HTTP `403 Forbidden`.
   * Public APIs (`/api/**`) protected via Role-Based Access Control (`AIRCRAFT_MECHANIC`, `MAINTENANCE_TECHNICIAN`, `MAINTENANCE_SUPERVISOR`, `ADMIN`).
   * Non-repudiation audit logging via `AuditLogFilter` to dedicated `AUDIT` logger.

---

## 3. Port Allocation & Multi-Instance Cluster Scheme

| Component / Replica | Application Port | Database Engine & Port | Peer URLs | Active Profile |
| :--- | :--- | :--- | :--- | :--- |
| **Instance 1 (`aircraft-1`)** | `http://localhost:8081` | PostgreSQL: `5431` / H2 mem | `http://localhost:8091` | `instance1` |
| **Instance 2 (`aircraft-2`)** | `http://localhost:8091` | PostgreSQL: `5434` / H2 mem | `http://localhost:8081` | `instance2` |
| **Instance 3 (`aircraft-3`)** | `http://localhost:8101` | PostgreSQL: `5437` / H2 mem | `http://localhost:8081,8091` | `instance3` |

---

## 4. How to Run the Service

### Option A: Local Multi-Instance Cluster (Recommended for Rapid Testing)
Starts multiple instances locally in the background using lightweight in-memory H2:

```bash
# Start 2 instances (8081 and 8091):
./scripts/run-local.sh

# Start 3 instances (8081, 8091, 8101):
./scripts/run-local.sh -n 3

# With HTTPS / TLS 1.3 encryption in transit:
./scripts/run-local.sh --tls

# With PostgreSQL instead of H2:
./scripts/run-local.sh --postgres
```

### Option B: Docker Compose Multi-Container Cluster
Builds multi-stage Docker images (Temurin 21) and orchestrates 3 instances with independent PostgreSQL databases:

```bash
# Start 3 instances with dedicated PostgreSQL databases:
./scripts/run-docker.sh

# Or with lightweight in-memory H2 (no database containers needed):
./scripts/run-docker.sh --h2

# Stop cluster:
./scripts/run-docker.sh down
```

### Option C: Single Instance via Maven
```bash
export JAVA_HOME=/home/andrepm2004/.jdks/corretto-21.0.12.1
./mvnw spring-boot:run
```

---

## 5. Live Demonstration & Verification

### 1. Interactive Cluster Demonstration Script
With instances running, execute the automated demonstration script to validate all 7 distributed scenarios live:
```bash
# HTTP:
./scripts/demo-cluster.sh

# HTTPS / TLS 1.3:
./scripts/demo-cluster.sh --tls
```
*Outputs color-coded validation of Local Hit, P2P Forwarding, Reverse Forwarding, Loop Prevention, Security Boundary, and Maintenance Reports.*

### 2. JUnit Automated Test Suite (150 Tests)
```bash
./mvnw clean test
```
*Executes 150 automated unit and integration tests covering domain invariants, MockMvc controllers, security filters, 3-instance cluster replication, Zero SPOF, and peer forwarding.*

### 3. Newman / Postman Collection
```bash
# Plain HTTP:
npx -y newman run postman/aircraft-maintenance.postman_collection.json -e postman/local.postman_environment.json

# HTTPS / TLS 1.3 (verifying trusted CA certificate):
npx -y newman run postman/aircraft-maintenance.postman_collection.json -e postman/local-https.postman_environment.json --ssl-extra-ca-certs certs/ca.crt
```
*Validates full contract compliance against active cluster instances.*

---

## 6. Project Documentation Index

* [docs/architecture.md](docs/architecture.md): **Comprehensive Architectural & Distributed Design Document** (Theoretical foundations, 8 Fallacies, CAP/PACELC trade-offs, Kruchten's 4+1 views, FURPS+, and ADRs).
* [docs/C4-Model.md](docs/C4-Model.md): **C4+1 Model Diagrams** (Context, Container, Component, Deployment, and Sequence diagrams).
* [docs/service-contracts.md](docs/service-contracts.md): **Inter-Service REST Contract Agreement** (Shared across all 3 microservices).
* [requests.http](requests.http): **IntelliJ HTTP Client Requests** for manual API execution.
