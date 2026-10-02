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

See [HANDOFF.md](HANDOFF.md): what Flight Operations needs from this service, what to port from the monolith,
and which Flight Operations code to reuse for distribution, resilience and security.
