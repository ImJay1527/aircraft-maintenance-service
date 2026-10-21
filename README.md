# aircraft-maintenance-service

AISafe Flight Management System – **Aircraft & Maintenance** microservice (SIDIS 2026/27, Assignment 1).

## Overview

Decomposed from the monolithic PSOFT legacy application using Domain-Driven Segregation. This microservice is responsible for:
- **Aircraft Models & Specifications:** Seating capacity, fuel capacity, maximum operational range, cruise speed.
- **Aircraft Fleet Management:** Registration numbers, operational status (`AVAILABLE`, `IN_FLIGHT`, `UNDER_MAINTENANCE`, `INACTIVE`), flight hours tracking.
- **Maintenance Templates:** Checklists and scheduled maintenance definitions (Routine A-Check, C-Check, Engine Overhaul, Modifications).
- **Maintenance Execution & Records:** Tracking active and completed maintenance interventions, turnaround times, and maintenance costs per aircraft.

## Architecture & Distributed Systems (SIDIS)

- **Zero Single Point of Failure (Zero SPOF):** Active multi-instance replication (Component Cloning) with independent database per replica (H2 in-memory or PostgreSQL).
- **P2P Unicast Query Forwarding:** If an aircraft is queried on an instance that does not store it locally, the instance forwards the request to peer replicas via HTTP GET using service-to-service authentication.
- **Loop Prevention:** Encaminhamento em anel é prevenido através do cabeçalho `X-Forwarded-From`. Se uma instância recebe um pedido com este cabeçalho, apenas pesquisa localmente e nunca propaga para outros nós.
- **Data Provenance:** Headers `X-Data-Source: local` e `X-Data-Source: peer` identificam a proveniência da resposta.
- **Inter-service Security:** Endpoints sob `/internal/**` estão protegidos por JWT e restritos ao papel `SERVICE`. Endpoints públicos sob `/api/**` são protegidos por RBAC (`AIRCRAFT_MECHANIC`, `MAINTENANCE_TECHNICIAN`, `MAINTENANCE_SUPERVISOR`, `ADMIN`).

## Port Conventions

- **Instance 1:** `http://localhost:8081` (DB: `5431` in PostgreSQL)
- **Instance 2:** `http://localhost:8091` (DB: `5434` in PostgreSQL)
- **Instance 3:** `http://localhost:8101` (DB: `5437` in PostgreSQL)

## Running the Service

### Option A: Local Multi-Instance (Fastest)

```bash
# Starts 2 instances locally (8081 and 8091) with in-memory H2:
./scripts/run-local.sh

# Starts 3 instances:
./scripts/run-local.sh -n 3

# With PostgreSQL instead of H2:
./scripts/run-local.sh --postgres
```

### Option B: Docker Compose

```bash
# Start 2 instances with independent PostgreSQL containers:
./scripts/run-docker.sh

# Stop containers:
./scripts/run-docker.sh down
```

### Option C: Single Instance with Maven

```bash
./mvnw spring-boot:run
```

## Testing & Quality Assurance

- **JUnit Integration Tests:** 14 automated test cases covering domain logic, controllers, security, and peer forwarding:
  ```bash
  ./mvnw clean test
  ```
- **Postman / Newman Test Suite:** 15 requests and 29 assertions covering health, service contracts, peer forwarding, loop prevention, and maintenance reports:
  ```bash
  npx -y newman run postman/aircraft-maintenance.postman_collection.json -e postman/local.postman_environment.json
  ```

## Documentation

- [docs/architecture.md](docs/architecture.md): Complete architectural documentation (DDD, CAP trade-offs, Zero SPOF, P2P protocols).
- [docs/C4-Model.md](docs/C4-Model.md): C4 architecture model (Context, Container, Component) and +1 Sequence Diagram.
- [docs/service-contracts.md](docs/service-contracts.md): Inter-service REST contract shared across all 3 microservices.
- [requests.http](requests.http): Ready-to-use HTTP requests for IntelliJ IDEA HTTP Client.
