# aircraft-maintenance-service

AISafe Flight Management System – **Aircraft & Maintenance** service (SIDIS 2026/27, Assignment 1).

Owns: aircraft models, aircraft, operational status, maintenance templates and records.
Contract with the other services: [docs/service-contracts.md](docs/service-contracts.md).

> **Skeleton.** Security (JWT, roles, `/internal/**` = service-only, audit log), error handling, Dockerfile and config
> are ready. The domain still has to be ported from the PSOFT monolith.

## Run

```bash
./mvnw spring-boot:run          # port 8081, H2 in memory
```

## TODO (owner)

1. Port from the monolith (`pt.isep.psoft.alsafe.*`):
   - `aircraftmanagement` (all of it)
   - `maintenancemanagement` (all of it)
   - the aircraft / model / maintenance parts of `bootstrap.Bootstrapper` (use the IDs in the contract doc)
2. Remove anything that touched other modules directly (e.g. US203 *compatible routes* needs routes →
   call airports-routes-service over HTTP, or move the endpoint).
3. Implement `internal/InternalAircraftController` (currently returns 501). Flight Operations depends on it.
4. Replication: store data sharded per replica, and make GETs ask the peers (`aircraft.peers`) when the data isn't
   local. See `flight-operations-service` (`peers/PeerClient`, `services/FlightQueryService`) for a working example.
5. Users log in on flight-operations-service. This service only **validates** tokens, so there is no user table here.
