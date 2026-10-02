# Handoff: Aircraft & Maintenance service

From the Flight Operations owner. This explains what is already in the skeleton, what Flight Operations needs from
your service, and how the requirements of P1 / PL2 / PL3 were solved on the Flight Operations side, so you can reuse
the code instead of starting from zero.

## 1. What the skeleton already has

- Spring Boot 3.3, Java 21, Maven wrapper, Dockerfile, port 8081
- JWT validation with the shared secret (`JWT_SECRET`), roles, `/internal/**` restricted to role `SERVICE`
- `AUDIT` log of every request, `GlobalExceptionHandler` (404 / 503)
- `internal/InternalAircraftController` + `AircraftInfo`, currently answering **501**
- `docs/service-contracts.md`: the agreement between the three services. Read it first.

Users log in on flight-operations-service (`POST /api/auth/login`). Your service only validates tokens, so there is
no user table here.

## 2. What Flight Operations needs from you (do this first)

Flight Operations calls exactly one endpoint of yours, to validate a booking:

```
GET /internal/aircraft/{registration}        Authorization: Bearer <SERVICE token>
```

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

How each field is used when a flight is booked:

| Field | Rule in Flight Operations |
|---|---|
| `status` | must be `AVAILABLE` (one of `AVAILABLE`, `IN_FLIGHT`, `UNDER_MAINTENANCE`, `INACTIVE`) |
| `modelName` | origin and destination airports must be certified for it (exact string, e.g. `737 MAX`) |
| `maxRange` | must be >= the route's distance (km) |
| `activeCapacity` | must be >= the route's minimum capacity (seats) |
| `fuelCapacity` | `fuelCapacity / maxRange` is stored as the flight's fuel burn rate (US227) |

**Status codes matter:**

- `200` with the JSON above.
- `404` **only when no instance of your service has the aircraft.** Flight Operations treats 404 as final and does not
  try your other instance. So an instance that doesn't hold the aircraft locally must ask its peer before
  answering 404 (PL3 p.11).
- Anything else (5xx, timeout, connection refused) makes Flight Operations retry on your other instance
  (round-robin with failover, then circuit breaker).
- `501` (what the skeleton returns now) shows up to users as `503 "... does not implement GET /internal/aircraft/...
  yet"`. That message disappears once the endpoint is implemented.

Extra JSON fields are ignored, so returning more than this is fine.

**Bootstrap data:** use the IDs in the contract doc so the services line up. Flight Operations' test data
(`stub` profile, Postman collection) also uses these:

| Registration | Status | Model | Range km | Fuel l | Seats |
|---|---|---|---|---|---|
| CS-TPA, CS-TPC, CS-TPF, CS-TPH, CS-TPJ | AVAILABLE | A320neo | 6300 | 24000 | 160 |
| CS-TPB, CS-TPG, CS-TPI | AVAILABLE | 737 MAX | 6500 | 26000 | 180 |
| CS-TPD | AVAILABLE | 777X | 8000 | 35000 | 400 |
| CS-TPE | IN_FLIGHT | A350 | 15000 | 140000 | 350 |
| CS-TPM | UNDER_MAINTENANCE | A320neo | 6300 | 24000 | 160 |

If you bootstrap the same aircraft, the Postman collection of flight-operations-service also passes against the
real services, not only against its stub.

## 3. Porting from the monolith

From `pt.isep.psoft.alsafe.*`:

- `aircraftmanagement` (all of it)
- `maintenancemanagement` (all of it)
- the aircraft / model / maintenance parts of `bootstrap.Bootstrapper`

Then remove anything that touched other modules directly. For example, US203 *compatible routes* needs routes:
call airports-routes-service over HTTP (`/internal/routes/{id}`), or agree with its owner to move the endpoint.
Each service has its own database, and cross-references are by ID only.

## 4. Distribution: how Flight Operations did it (copy what you need)

The team decision was to follow PL3 (week 3): **2 instances per service, each with its own database, the data split
between them, no replication.** Paths below are in `flight-operations-service/src/main/java/pt/isep/sidis/flightops/`.

| Requirement | Flight Operations solution | Files to look at |
|---|---|---|
| Hardcoded peer list (PL3 p.12) | `flightops.cluster=instance1=url,instance2=url`, the same list on every instance | `cluster/Cluster.java`, `application-instance1.properties` |
| Who stores what (P1 p.15) | rendezvous hashing on the key: every instance computes the same owner, with no coordination | `Cluster.ownerOf` |
| Not found locally → ask peers (PL3 p.11) | public `/api/...` endpoint looks locally, then asks peers on `/internal/...` endpoints that **only** look locally (no forwarding loops) | `peers/PeerClient.java`, `api/InternalFlightController.java` |
| Peer down (PL3 p.12, p.14) | timeouts, retries with backoff for GETs, circuit breaker, health checks every 5 s | `resilience/*` |
| Pooled HTTP client, TLS | Apache HttpClient 5 via `RestClient` | `clients/HttpClientFactory.java` (+ `httpclient5` in `pom.xml`) |
| Tracing (PL3 p.19) | `X-Request-Id` passed between services, `[instance] [requestId]` in every log line | `common/tracing/*`, `logging.pattern.console` |

For your service: shard aircraft by `registrationNumber` and maintenance records by the aircraft they belong to, so
an aircraft and its maintenance history live on the same instance.

**Easiest path for the internal endpoint:** an instance answers `GET /internal/aircraft/{reg}` from its own database;
if it doesn't have it, it asks the owner (`ownerOf(reg)`) through a local-only endpoint, and only then answers 404.

## 5. Security (P1 p.16)

- **TLS 1.3:** copy `src/main/resources/application-tls.properties` from flight ops and rename the alias to
  `aircraft`. The certificates are already generated by `flight-operations-service/scripts/generate-dev-certs.sh`
  (`certs/aircraft.p12`, valid for `aircraft`, `aircraft-1..3` and `localhost`).
- **Encryption at rest:** `common/crypto/*` (deterministic AES, so equality queries still work), used with
  `@Convert(converter = EncryptedString.class)` on the entity fields.
- **Faster JWT handling:** replace `JwtUtils` and `AuthTokenFilter` with the flight-ops versions. They build the parser
  once and cache service tokens; under load the skeleton version is noticeably slower.

## 6. Database and Docker

- PostgreSQL per instance: copy `application-postgres.properties` and the `x-flightops-db` block of
  `flight-operations-service/docker-compose.yml`.
- The whole system starts from `flight-operations-service`: `docker compose up --build` (your repo must be cloned next
  to it, in `../aircraft-maintenance-service`). It already starts `aircraft-1` (port 8081) and `aircraft-2`
  (port 8091), with `PEERS` pointing at each other.
- When you switch to TLS, tell me: the compose file and `AIRCRAFT_SERVICE_URLS` need `https://` and the certificate
  mount (there is a comment in the compose file about this).

## 7. Checklist

- [ ] `GET /internal/aircraft/{reg}`: 200 with the JSON above, 404 only after asking the peer
- [ ] Bootstrap the aircraft from section 2
- [ ] Port `aircraftmanagement` and `maintenancemanagement`, with no imports of other modules
- [ ] 2 instances, data sharded, peers asked when data isn't local
- [ ] Timeouts, retries and circuit breaker on peer calls
- [ ] TLS 1.3 profile, encryption at rest
- [ ] PostgreSQL per instance
- [ ] Postman collection for your endpoints (PL3 p.16-17)
- [ ] Your part of the documentation (architecture, diagrams, load tests). See `flight-operations-service/docs/`
      for the format we used

**Quick integration test:** with your service on 8081, run `./scripts/run-local.sh --h2` in
flight-operations-service (real services, no Docker needed), log in with `POST /api/auth/login` as `atcc` / `atcc123`,
then `POST /api/scheduled-flights` with `route-opo-lis` and `CS-TPA`. If it returns 201, your side of the contract
works. Airports & Routes must be running too, or Flight Operations answers 503 for the route.
