# AISafe Flight Management System - C4+1 Architectural Model
**Component**: `aircraft-maintenance-service`  
**Course**: Sistemas Distribuídos (LETI-SIDIS 2026/27)  
**Author**: André (1240567)  

---

## 1. Introduction & Notation

This document formalizes the architectural design of the **Aircraft & Maintenance Service** using the **C4 model** (Context, Containers, Components, Code) originated by Simon Brown, combined with the dynamic "+1" scenarios of Philippe Kruchten's **4+1 View Model** (IEEE Software, 1995).

---

## Level 1: System Context Diagram

Visualizes the high-level boundaries of the AISafe distributed system, human actors, and the operational responsibilities of the maintenance division.

```mermaid
%% C4 Level 1 - System Context
flowchart TB
    classDef person fill:#08427b,stroke:#052e56,color:#fff;
    classDef system fill:#1168bd,stroke:#0b4884,color:#fff;

    mech["Aircraft Mechanic / Technician<br/>[Person]<br/>Conducts inspections & logs maintenance hours/costs"]:::person
    sup["Maintenance Supervisor<br/>[Person]<br/>Configures templates, checklists & inspects turnaround reports"]:::person
    disp["Flight Operations Dispatcher<br/>[Person]<br/>Schedules flights and verifies airworthiness"]:::person

    aisafe["AISafe Flight Management System<br/>[Distributed Software System]<br/>Coordinates flights, fleet availability, and airport routing"]:::system

    mech -->|"HTTP/REST + JWT<br/>Update maintenance status & complete work orders"| aisafe
    sup -->|"HTTP/REST + JWT<br/>Approve templates & monitor fleet airworthiness"| aisafe
    disp -->|"HTTP/REST + SERVICE JWT<br/>Verify aircraft range & operational readiness"| aisafe
```

---

## Level 2: Container Diagram (Domain-Driven Segregation)

Illustrates the decomposition of the original PSOFT monolith into three autonomous, domain-aligned microservices, highlighting active component cloning and isolated databases.

```mermaid
%% C4 Level 2 - Container Diagram
flowchart TB
    classDef person fill:#08427b,stroke:#052e56,color:#fff;
    classDef container fill:#438dd5,stroke:#2e6295,color:#fff;
    classDef db fill:#2e6295,stroke:#11365c,color:#fff,shape:cylinder;

    Users[("Operators, Mechanics & Dispatchers<br/>[Person]")]:::person

    subgraph AISafe["AISafe Distributed Architecture (Zero SPOF)"]
        subgraph AM_Cluster["aircraft-maintenance-service (This Component)"]
            am1["aircraft-1<br/>[Container: Spring Boot 3]<br/>Port :8081"]:::container
            am2["aircraft-2<br/>[Container: Spring Boot 3]<br/>Port :8091"]:::container
            db_am1[("aircraft-db-1<br/>[PostgreSQL 16 / H2]<br/>Port :5431")]:::db
            db_am2[("aircraft-db-2<br/>[PostgreSQL 16 / H2]<br/>Port :5434")]:::db
            am1 --- db_am1
            am2 --- db_am2
            am1 <-->|"P2P Unicast Forwarding<br/>GET /internal/aircraft/{reg}<br/>(SERVICE JWT, X-Forwarded-From)"| am2
        end

        subgraph FO_Cluster["flight-operations-service"]
            fo1["flightops-1<br/>[Container: Spring Boot 3]<br/>Port :8083"]:::container
            fo2["flightops-2<br/>[Container: Spring Boot 3]<br/>Port :8093"]:::container
            db_fo[("flightops-db<br/>[PostgreSQL 16 / H2]")]:::db
            fo1 --- db_fo
            fo2 --- db_fo
        end

        subgraph AR_Cluster["airports-routes-service"]
            ar1["airports-1<br/>[Container: Spring Boot 3]<br/>Port :8082"]:::container
            ar2["airports-2<br/>[Container: Spring Boot 3]<br/>Port :8092"]:::container
            db_ar[("airports-db<br/>[PostgreSQL 16 / H2]")]:::db
            ar1 --- db_ar
            ar2 --- db_ar
        end
    end

    Users -->|"HTTPS / REST + User JWT"| am1
    Users -->|"HTTPS / REST + User JWT"| fo1
    Users -->|"HTTPS / REST + User JWT"| ar1

    fo1 -->|"Inter-Service Query<br/>GET /internal/aircraft/{reg}<br/>(Role: SERVICE)"| am1
    fo1 -->|"Inter-Service Query<br/>GET /internal/routes/{id}<br/>(Role: SERVICE)"| ar1
```

---

## Level 3: Component Diagram (Inside an `aircraft-maintenance-service` Instance)

Details the internal components of a single instance, including the security and distributed tracing filter chain, REST controllers, domain services, P2P peer client, and data persistence repositories.

```mermaid
%% C4 Level 3 - Component Diagram
flowchart TB
    classDef filter fill:#d97706,stroke:#92400e,color:#fff;
    classDef ctrl fill:#1168bd,stroke:#0b4884,color:#fff;
    classDef svc fill:#2563eb,stroke:#1d4ed8,color:#fff;
    classDef repo fill:#475569,stroke:#1e293b,color:#fff;
    classDef db fill:#1e3a8a,stroke:#172554,color:#fff,shape:cylinder;

    dbEngine[("Isolated DB<br/>[H2 / PostgreSQL 16]")]:::db

    subgraph Instance["aircraft-maintenance-service Instance"]
        subgraph Pipeline["Filter Chain (Ordered)"]
            direction LR
            fTrace["RequestTracingFilter<br/>(Order: HIGHEST)<br/>X-Request-Id, X-Instance, MDC"]:::filter
            fAuth["AuthTokenFilter<br/>JWT parsing & validation"]:::filter
            fAudit["AuditLogFilter<br/>Non-repudiation audit logging"]:::filter
            fTrace --> fAuth --> fAudit
        end

        subgraph Controllers["REST Presentation Layer"]
            direction TB
            cInternal["InternalAircraftController<br/>Handles /internal/aircraft/{reg}<br/>Enforces SERVICE role & P2P forwarding"]:::ctrl
            cAircraft["AircraftController<br/>Handles /api/aircrafts/**<br/>Roles: MECHANIC, ADMIN"]:::ctrl
            cTemplate["MaintenanceTemplateController<br/>Handles /api/maintenance-templates/**<br/>Roles: TECHNICIAN, SUPERVISOR"]:::ctrl
            cRecord["MaintenanceRecordController<br/>Handles /api/maintenance-records/**<br/>Roles: TECHNICIAN, SUPERVISOR"]:::ctrl
        end

        subgraph Core["Domain Services & Peer Coordination"]
            direction TB
            sAircraft["AircraftService<br/>Fleet lifecycle & flight hours tracking"]:::svc
            sTemplate["MaintenanceTemplateService<br/>Checklist definitions & model applicability"]:::svc
            sRecord["MaintenanceRecordService<br/>Work order lifecycle, turnaround & cost calculations"]:::svc
            sPeer["HttpPeerClient<br/>Unicast HTTP GET with connect/read timeout"]:::svc
            cfgCluster["ClusterConfig<br/>Self identity & Peer URLs"]:::svc
        end

        subgraph Repos["Data Access Layer (Spring Data JPA)"]
            direction LR
            rAircraft[("AircraftRepository")]:::repo
            rModel[("AircraftModelRepository")]:::repo
            rTemplate[("MaintenanceTemplateRepository")]:::repo
            rRecord[("MaintenanceRecordRepository")]:::repo
        end
    end

    Pipeline --> Controllers

    cInternal --> rAircraft
    cInternal --> sPeer
    sPeer --> cfgCluster
    sPeer -->|"Unicast HTTP GET<br/>X-Forwarded-From / SERVICE JWT"| extPeer(["Peer Replica Instance"])

    cAircraft --> sAircraft
    cTemplate --> sTemplate
    cRecord --> sRecord

    sAircraft --> rAircraft
    sAircraft --> rModel
    sTemplate --> rTemplate
    sTemplate --> rModel
    sRecord --> rRecord
    sRecord --> rAircraft
    sRecord --> rTemplate

    rAircraft --- dbEngine
    rModel --- dbEngine
    rTemplate --- dbEngine
    rRecord --- dbEngine
```

---

## Level 4: Deployment Diagram (Physical & Virtual Container Topology)

Details the physical/virtual deployment architecture within Docker, illustrating listening ports, network aliases, and dedicated storage volumes.

```mermaid
%% C4 Level 4 - Deployment Diagram
flowchart TB
    classDef client fill:#334155,stroke:#1e293b,color:#fff;
    classDef app fill:#0284c7,stroke:#0369a1,color:#fff;
    classDef db fill:#16a34a,stroke:#15803d,color:#fff;
    classDef vol fill:#ea580c,stroke:#c2410c,color:#fff,stroke-dasharray: 5 5;

    clientNode["Client Application / Postman / Flight Operations"]:::client

    subgraph DockerHost["Docker Host Environment (Linux Bridge 172.20.0.0/16)"]
        subgraph AppCluster["Application Tier (Eclipse Temurin 21)"]
            inst1["Container: aircraft-1<br/>Port in container: 8081<br/>Exposed Host Port: 8081:8081<br/>Active Profiles: instance1,postgres"]:::app
            inst2["Container: aircraft-2<br/>Port in container: 8081<br/>Exposed Host Port: 8091:8081<br/>Active Profiles: instance2,postgres"]:::app
        end

        subgraph DBCluster["Persistence Tier (PostgreSQL 16 Alpine)"]
            db1[("Container: aircraft-db-1<br/>Port in container: 5432<br/>Exposed Host Port: 5431:5432<br/>Healthcheck: pg_isready")]:::db
            db2[("Container: aircraft-db-2<br/>Port in container: 5432<br/>Exposed Host Port: 5434:5432<br/>Healthcheck: pg_isready")]:::db
        end

        subgraph VolumeTier["Persistent Named Volumes"]
            v1[("Volume: aircraft-db-1-data<br/>Mount: /var/lib/postgresql/data")]:::vol
            v2[("Volume: aircraft-db-2-data<br/>Mount: /var/lib/postgresql/data")]:::vol
        end
    end

    clientNode -->|"HTTP GET :8081"| inst1
    clientNode -->|"HTTP GET :8091"| inst2

    inst1 <-->|"P2P Unicast Forwarding<br/>http://aircraft-2:8081<br/>(X-Forwarded-From, SERVICE JWT)"| inst2

    inst1 -->|"JDBC: postgresql://aircraft-db-1:5432/aircraft"| db1
    inst2 -->|"JDBC: postgresql://aircraft-db-2:5432/aircraft"| db2

    db1 --- v1
    db2 --- v2
```

---

## The "+1": Dynamic Distributed Behavior (Sequence Diagrams)

### Scenario 1: P2P Unicast Query Forwarding Across Replicas
Demonstrates how Instance 1 satisfies a query for partition `CS-TPX` (residing natively on Instance 2) while attaching correlation and provenance headers.

```mermaid
%% Sequence Diagram: P2P Query Forwarding
sequenceDiagram
    autonumber
    participant Caller as Calling Client (Flight Operations / Postman)
    participant A1 as aircraft-1 (Port 8081)
    participant DB1 as Database 1 (Local Shard)
    participant A2 as aircraft-2 (Port 8091)
    participant DB2 as Database 2 (Remote Shard)

    Caller->>A1: GET /internal/aircraft/CS-TPX<br/>Authorization: Bearer <SERVICE JWT><br/>X-Request-Id: req-101
    
    Note over A1: 1. RequestTracingFilter sets MDC & X-Instance: instance1
    Note over A1: 2. Query local database
    A1->>DB1: findByRegistrationNumber("CS-TPX")
    DB1-->>A1: Optional.empty() (Local Miss)

    Note over A1: 3. Verify X-Forwarded-From is absent.<br/>Invoke HttpPeerClient.fetchAircraftFromPeers()
    A1->>A2: GET /internal/aircraft/CS-TPX<br/>Authorization: Bearer <SERVICE JWT><br/>X-Forwarded-From: instance1<br/>X-Request-Id: req-101

    Note over A2: 4. A2 checks local database
    A2->>DB2: findByRegistrationNumber("CS-TPX")
    DB2-->>A2: Aircraft(CS-TPX, AVAILABLE, A320neo, 6300km)
    A2-->>A1: 200 OK + AircraftInfo JSON<br/>X-Data-Source: local<br/>X-Instance: instance2

    Note over A1: 5. Attach provenance header X-Data-Source: peer
    A1-->>Caller: 200 OK + AircraftInfo JSON<br/>X-Data-Source: peer<br/>X-Instance: instance1<br/>X-Request-Id: req-101
```

---

### Scenario 2: Distributed Loop Prevention (Cutting Cycle Storms)
Proves that recursive or circular forwarding requests are terminated at the first hop ($hop \le 1$).

```mermaid
%% Sequence Diagram: Loop Prevention
sequenceDiagram
    autonumber
    participant A2 as aircraft-2 (Calling Peer)
    participant A1 as aircraft-1 (Receiving Peer)
    participant DB1 as Database 1 (Local Shard)

    Note over A2,A1: A2 forwards a request for non-existent aircraft "CS-UNKNOWN"
    A2->>A1: GET /internal/aircraft/CS-UNKNOWN<br/>Authorization: Bearer <SERVICE JWT><br/>X-Forwarded-From: instance2<br/>X-Request-Id: req-202

    A1->>DB1: findByRegistrationNumber("CS-UNKNOWN")
    DB1-->>A1: Optional.empty() (Local Miss)

    Note over A1: Loop Prevention Check:<br/>X-Forwarded-From header is present ("instance2").<br/>CRITICAL: Do NOT query any peer. Stop propagation immediately.

    A1-->>A2: 404 Not Found<br/>X-Instance: instance1<br/>X-Request-Id: req-202
```
