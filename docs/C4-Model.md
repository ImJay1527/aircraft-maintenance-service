# AISafe Flight Management System - C4 Architecture Model

This document outlines the software architecture of the AISafe distributed system using the C4 model, specifically focusing on the **Aircraft & Maintenance Service**.

## Level 1: System Context
This diagram shows the high-level view of the AISafe system and its users.

```mermaid
flowchart TD
    %% Styling
    classDef person fill:#08427b,stroke:#052e56,color:#fff
    classDef system fill:#1168bd,stroke:#0b4884,color:#fff
    
    %% Nodes
    Op[("Backoffice Operator\n[Person]")]:::person
    Tech[("Maintenance Tech / Supervisor\n[Person]")]:::person
    Sys["AISafe Flight Management System\n[Software System]\n\nManages aircraft, routes, flights and maintenance."]:::system
    
    %% Relationships
    Op -- "Manages fleet and aircraft models" --> Sys
    Tech -- "Executes and logs maintenance records" --> Sys
```

## Level 2: Container Diagram
This diagram shows the microservices architecture that makes up the AISafe system, reflecting the Domain-Driven Segregation.

```mermaid
flowchart TD
    %% Styling
    classDef person fill:#08427b,stroke:#052e56,color:#fff
    classDef container fill:#438dd5,stroke:#2e6295,color:#fff
    classDef db fill:#2e6295,stroke:#11365c,color:#fff,shape:cylinder

    %% External
    Users[("Users (Web/Mobile App)\n[Person]")]:::person

    %% Containers
    subgraph AISafe["AISafe System"]
        AircraftSvc["Aircraft & Maintenance Service\n[Container: Spring Boot]\n\nManages fleet status and maintenance."]:::container
        AirportSvc["Airports & Routes Service\n[Container: Spring Boot]\n\nManages infrastructure and network."]:::container
        FlightSvc["Flight Operations Service\n[Container: Spring Boot]\n\nOrchestrates schedules and flights."]:::container
        
        AircraftDB[("Aircraft DB\n[Container: H2/PostgreSQL]")]:::db
        AirportDB[("Airports DB\n[Container: H2/PostgreSQL]")]:::db
        FlightDB[("Flights DB\n[Container: H2/PostgreSQL]")]:::db
    end

    %% Relationships
    Users -- "HTTP/REST + JWT" --> AircraftSvc
    Users -- "HTTP/REST + JWT" --> AirportSvc
    Users -- "HTTP/REST + JWT" --> FlightSvc

    AircraftSvc -- "Reads/Writes" --> AircraftDB
    AirportSvc -- "Reads/Writes" --> AirportDB
    FlightSvc -- "Reads/Writes" --> FlightDB

    %% Inter-service communications
    FlightSvc -. "GET /internal/aircraft/{reg} (SERVICE JWT)" .-> AircraftSvc
    FlightSvc -. "GET /internal/routes/{id} (SERVICE JWT)" .-> AirportSvc
    AircraftSvc -. "P2P Forwarding (SERVICE JWT)" .-> AircraftSvc
    AirportSvc -. "P2P Forwarding (SERVICE JWT)" .-> AirportSvc
```

## Level 3: Component Diagram (Aircraft & Maintenance Service)
Focusing specifically on the components inside `aircraft-maintenance-service`.

```mermaid
flowchart TD
    %% Styling
    classDef container fill:#438dd5,stroke:#2e6295,color:#fff
    classDef component fill:#1168bd,stroke:#0b4884,color:#fff
    classDef db fill:#2e6295,stroke:#11365c,color:#fff,shape:cylinder

    AircraftDB[("Aircraft DB\n[H2 / PostgreSQL]")]:::db

    subgraph AircraftSvc["Aircraft & Maintenance Service"]
        SecurityFilter["AuthTokenFilter & AuditLogFilter\n[Spring Security]\nValidates JWT and audits requests"]:::component
        
        InternalCtrl["InternalAircraftController\n[REST Controller]\nHandles /internal/aircraft/{reg}\nwith loop prevention"]:::component
        AircraftCtrl["AircraftController\n[REST Controller]\nHandles /api/aircrafts"]:::component
        TemplateCtrl["MaintenanceTemplateController\n[REST Controller]\nHandles /api/maintenance-templates"]:::component
        RecordCtrl["MaintenanceRecordController\n[REST Controller]\nHandles /api/maintenance-records"]:::component

        AircraftSvcDomain["AircraftService\n[Spring Service]\nFleet lifecycle & flight hours"]:::component
        TemplateSvcDomain["MaintenanceTemplateService\n[Spring Service]\nTemplate & checklist logic"]:::component
        RecordSvcDomain["MaintenanceRecordService\n[Spring Service]\nInterventions, turnaround & cost reports"]:::component

        ClusterCfg["ClusterConfig\n[Component]\nInstance identity & peer topology"]:::component
        HttpPeer["HttpPeerClient (PeerClient)\n[Component: RestClient]\nUnicast query forwarding with timeout"]:::component

        Repos["Spring Data JPA Repositories\n[AircraftRepository, MaintenanceRecordRepository, ...]\nData Access Layer"]:::component
    end

    %% Wire connections
    SecurityFilter --> InternalCtrl
    SecurityFilter --> AircraftCtrl
    SecurityFilter --> TemplateCtrl
    SecurityFilter --> RecordCtrl

    InternalCtrl --> Repos
    InternalCtrl --> HttpPeer
    HttpPeer --> ClusterCfg

    AircraftCtrl --> AircraftSvcDomain
    TemplateCtrl --> TemplateSvcDomain
    RecordCtrl --> RecordSvcDomain

    AircraftSvcDomain --> Repos
    TemplateSvcDomain --> Repos
    RecordSvcDomain --> Repos
    Repos --> AircraftDB
```

## The "+1": Dynamic Behavior (Sequence Diagram)
The **C4+1** model combines static architecture with Kruchten's "4+1" dynamic view. This diagram documents the **P2P Query Forwarding with Loop Prevention** (Week 3 Distributed Consistency).

```mermaid
sequenceDiagram
    autonumber
    actor Caller as Flight Operations / Client
    participant A1 as Aircraft Instance 1 (:8081)
    participant DB1 as Database 1
    participant A2 as Aircraft Instance 2 (:8091)
    participant DB2 as Database 2

    Caller->>A1: GET /internal/aircraft/CS-TPX<br/>Authorization: Bearer <SERVICE JWT>
    Note over A1: 1. Check local database
    A1->>DB1: findByRegistrationNumber("CS-TPX")
    DB1-->>A1: Optional.empty (Local Miss)

    Note over A1: 2. Check X-Forwarded-From.<br/>Header absent -> Forward to peer
    A1->>A2: GET /internal/aircraft/CS-TPX<br/>Authorization: Bearer <SERVICE JWT><br/>X-Forwarded-From: instance1<br/>X-Request-Id: req-101

    Note over A2: 3. Peer checks local database
    A2->>DB2: findByRegistrationNumber("CS-TPX")
    DB2-->>A2: Aircraft(CS-TPX, AVAILABLE, A320neo)
    A2-->>A1: 200 OK + AircraftInfo JSON<br/>X-Data-Source: local

    Note over A1: 4. Aggregate and return
    A1-->>Caller: 200 OK + AircraftInfo JSON<br/>X-Data-Source: peer

    %% Loop prevention note
    opt Circular Loop Prevention Scenario
        Note over A2: If CS-UNKNOWN was requested:<br/>DB2 returns empty.<br/>A2 sees X-Forwarded-From: instance1.<br/>A2 returns 404 immediately (does NOT forward back).
    end
```
