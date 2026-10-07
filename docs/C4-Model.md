# AISafe Flight Management System - C4 Architecture Model

This document outlines the software architecture of the AISafe distributed system using the C4 model.

## Level 1: System Context
This diagram shows the high-level view of the AISafe system and its users.

```mermaid
flowchart TD
    %% Styling
    classDef person fill:#08427b,stroke:#052e56,color:#fff
    classDef system fill:#1168bd,stroke:#0b4884,color:#fff
    
    %% Nodes
    Op[("Backoffice Operator\n[Person]")]:::person
    Tech[("Maintenance Tech\n[Person]")]:::person
    Sys["AISafe Flight Management System\n[Software System]\n\nManages aircraft, routes, flights and maintenance."]:::system
    
    %% Relationships
    Op -- "Manages fleet and routes" --> Sys
    Tech -- "Logs maintenance records" --> Sys
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
    Users -- "HTTPS/REST" --> AircraftSvc
    Users -- "HTTPS/REST" --> AirportSvc
    Users -- "HTTPS/REST" --> FlightSvc

    AircraftSvc -- "Reads/Writes" --> AircraftDB
    AirportSvc -- "Reads/Writes" --> AirportDB
    FlightSvc -- "Reads/Writes" --> FlightDB

    %% Inter-service communications
    FlightSvc -. "Fetches aircraft data (HTTPS)" .-> AircraftSvc
    FlightSvc -. "Fetches route data (HTTPS)" .-> AirportSvc
    AircraftSvc -. "P2P Replication (HTTPS)" .-> AircraftSvc
    AirportSvc -. "P2P Replication (HTTPS)" .-> AirportSvc
```

## Level 3: Component Diagram (Aircraft & Maintenance Service)
Focusing specifically on your service (Aircraft & Maintenance).

```mermaid
flowchart TD
    %% Styling
    classDef container fill:#438dd5,stroke:#2e6295,color:#fff
    classDef component fill:#1168bd,stroke:#0b4884,color:#fff
    classDef db fill:#2e6295,stroke:#11365c,color:#fff,shape:cylinder

    AircraftDB[("Aircraft DB\n[Database]")]:::db

    subgraph AircraftSvc["Aircraft & Maintenance Service"]
        SecurityComp["Security & TLS Filter\n[Component: Spring Security]\nValidates JWT and Inter-service Auth"]:::component
        APIComp["REST Controllers\n[Component: Spring Web]\nExposes endpoints for Aircraft and Maintenance"]:::component
        AircraftDomain["Aircraft Service\n[Component: Spring Service]\nBusiness logic for Fleet Management"]:::component
        MaintenanceDomain["Maintenance Service\n[Component: Spring Service]\nBusiness logic for Maintenance"]:::component
        ReplicationComp["P2P Replication Client\n[Component: HTTP Client]\nForwards queries to other instances"]:::component
        RepoComp["JPA Repositories\n[Component: Spring Data]\nData Access Layer"]:::component
    end

    %% Relationships
    APIComp --> SecurityComp
    SecurityComp --> AircraftDomain
    SecurityComp --> MaintenanceDomain
    
    AircraftDomain --> RepoComp
    MaintenanceDomain --> RepoComp
    RepoComp --> AircraftDB
    
    AircraftDomain --> ReplicationComp
    ReplicationComp -. "Forward to Peer" .-> APIComp
```

## The "+1": Dynamic Behavior (Sequence Diagram)
The **C4+1** model combines the static architecture (C4) with Kruchten's "4+1" view model, where the "+1" represents the **Scenarios / Use Cases (Dynamic Behavior)**. 
To meet the SIDIS requirements, this diagram documents the most complex dynamic scenario of the project: the **HTTP Forwarding (P2P)** from *Week 3*.

```mermaid
sequenceDiagram
    actor Client
    participant API_A as Aircraft Svc (Node A)
    participant DB_A as Database A
    participant API_B as Aircraft Svc (Node B)
    participant DB_B as Database B

    Client->>API_A: GET /api/aircrafts/123
    API_A->>DB_A: Search ID "123"
    DB_A-->>API_A: Null (Not Found)
    
    Note over API_A,API_B: Start P2P Forwarding Logic (Week 3)
    
    API_A->>API_B: Forward HTTP GET /api/aircrafts/123
    API_B->>DB_B: Search ID "123"
    DB_B-->>API_B: Found!
    API_B-->>API_A: Returns Aircraft Data
    
    API_A-->>Client: Returns Aircraft Data
```
