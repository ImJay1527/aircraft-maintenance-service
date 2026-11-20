# AISafe Inter-Service AMQP Contract Specification
**Course**: Sistemas Distribuídos (LETI-SIDIS 2026/27)  
**Broker**: RabbitMQ 3 (`rabbitmq:3-management`)  
**Exchange**: `aisafe.events` (Topic Exchange, durable)  
**Dead Letter Exchange**: `aisafe.dlx` -> Dead Letter Queue: `aircraft.events.dlq`  
**Serialization**: JSON (`application/json`) via Spring AMQP `Jackson2JsonMessageConverter`

---

## 1. Overview & Broker Topology

Inter-service asynchronous communication in Assignment 2 is decoupled using a topic exchange pattern. Microservices publish domain events to the exchange `aisafe.events` with hierarchical routing keys.

To prevent message loss caused by competing consumers across replicated instances, **Publish/Subscribe per replica** is enforced: each replica binds its own dedicated queue (`aircraft.events.<instance>`) to `aisafe.events`.

```text
                               +----------------------------------+
                               | RabbitMQ Topic: "aisafe.events"  |
                               +----------------------------------+
                                 /               |              \
               flight.#         /     flight.#   |               \   flight.#
                               v                 v                v
                 +-------------------+ +-------------------+ +-------------------+
                 | aircraft.events.1 | | aircraft.events.2 | | aircraft.events.3 |
                 |    (replica 1)    | |    (replica 2)    | |    (replica 3)    |
                 +-------------------+ +-------------------+ +-------------------+
                           |                     |                     |
                           +---------------------+---------------------+
                                                 | (Dead-Letter on failure after 3 retries)
                                                 v
                                       +-------------------+
                                       |    aisafe.dlx     |
                                       +-------------------+
                                                 |
                                                 v
                                       +-------------------+
                                       |aircraft.events.dlq|
                                       +-------------------+
```

### Connection Configuration

| Parameter | Default Value | Environment Variable Override |
| :--- | :--- | :--- |
| **Host** | `localhost` (or `rabbitmq` in Docker) | `SPRING_RABBITMQ_HOST` |
| **Port** | `5672` | `SPRING_RABBITMQ_PORT` |
| **Username** | `guest` | `SPRING_RABBITMQ_USERNAME` |
| **Password** | `guest` | `SPRING_RABBITMQ_PASSWORD` |
| **Virtual Host** | `/` | `SPRING_RABBITMQ_VIRTUAL_HOST` |
| **Exchange Name** | `aisafe.events` | `aisafe.rabbitmq.exchange` |
| **Dead Letter Exchange** | `aisafe.dlx` | `aisafe.rabbitmq.dlx` |
| **Dead Letter Queue** | `aircraft.events.dlq` | `aisafe.rabbitmq.dlq` |
| **Management UI** | `http://localhost:15672` | (Login: `guest` / `guest`) |

---

## 2. Events Published by `aircraft-maintenance-service`

All events include standard metadata (`eventId`, `eventType`, `occurredAt`) and are dispatched via the **Transactional Outbox Pattern** to prevent phantom events on rollback and ensure at-least-once delivery.

### 2.1 `AircraftStatusChangedEvent`
* **Routing Key**: `aircraft.status.changed`
* **Triggered When**: An aircraft transitions between states (`AVAILABLE`, `IN_FLIGHT`, `UNDER_MAINTENANCE`, `INACTIVE` [grounded/AOG]).
* **Intended Consumer**: `flight-operations-service` (to know if an aircraft is airworthy or grounded).

#### JSON Schema & Example:
```json
{
  "eventId": "3c90f28e-5b1b-4f9e-a89e-b7d14e366112",
  "eventType": "AircraftStatusChangedEvent",
  "occurredAt": "2026-11-20T11:30:00Z",
  "registration": "CS-TPA",
  "previousStatus": "AVAILABLE",
  "newStatus": "UNDER_MAINTENANCE",
  "totalFlightHours": 1500.0
}
```
*(Note: deserializer supports both `previousStatus` and `oldStatus` via `@JsonAlias`).*

---

### 2.2 `AircraftCreatedEvent`
* **Routing Key**: `aircraft.created`
* **Triggered When**: A new aircraft instance is registered in the fleet.
* **Intended Consumers**: `flight-operations-service` (to maintain local read-model cache of range and fuel burn rates without HTTP queries), `airports-routes-service`.

#### JSON Schema & Example:
```json
{
  "eventId": "a7b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
  "eventType": "AircraftCreatedEvent",
  "occurredAt": "2026-11-20T11:30:00Z",
  "registration": "CS-TPA",
  "serialNumber": "SN-A320-001",
  "modelCode": "A320neo",
  "modelName": "Airbus A320neo",
  "maxRange": 6300.0,
  "fuelCapacity": 24000.0,
  "fuelBurnRate": 3.8095,
  "seatingCapacity": 180,
  "status": "AVAILABLE",
  "flightHours": 0.0
}
```
*(Note: `fuelBurnRate` is derived directly as `fuelCapacity / maxRange` in litres/km).*

---

### 2.3 `MaintenanceScheduledEvent`
* **Routing Key**: `aircraft.maintenance.scheduled`
* **Triggered When**: A work order is scheduled for an aircraft.
* **Intended Consumer**: `flight-operations-service` (to block flight dispatching during the maintenance window).

#### JSON Schema & Example:
```json
{
  "eventId": "4d5e6f7a-8b9c-0d1e-2f3a-4b5c6d7e8f9a",
  "eventType": "MaintenanceScheduledEvent",
  "occurredAt": "2026-11-20T11:30:00Z",
  "recordId": 1,
  "registration": "CS-TPA",
  "templateId": 1,
  "description": "Routine A-Check",
  "scheduledDate": "2026-11-22",
  "estimatedHours": 8.0,
  "component": "AIRFRAME",
  "estimatedCost": 1500.00
}
```

---

### 2.4 `MaintenanceCompletedEvent`
* **Routing Key**: `aircraft.maintenance.completed`
* **Triggered When**: A maintenance technician finishes work and approves return to service.
* **Intended Consumer**: `flight-operations-service` (unblocks the aircraft for flight scheduling).

#### JSON Schema & Example:
```json
{
  "eventId": "5e6f7a8b-9c0d-1e2f-3a4b-5c6d7e8f9a0b",
  "eventType": "MaintenanceCompletedEvent",
  "occurredAt": "2026-11-20T16:30:00Z",
  "recordId": 1,
  "registration": "CS-TPA",
  "actualHours": 7.5,
  "actualCost": 1400.00,
  "completionDetails": "All inspection checkpoints verified successfully"
}
```
*(Note: supports both `actualHours` / `loggedHours` and `actualCost` / `totalCost` via `@JsonAlias`).*

---

## 3. Events Consumed by `aircraft-maintenance-service`

Each replica binds its dedicated queue `aircraft.events.${aircraft.instance-name:instance1}` with routing pattern `flight.#` to consume flight lifecycle events.

### 3.1 `FlightCompletedEvent`
* **Expected Routing Key**: `flight.completed`
* **Publisher**: `flight-operations-service`
* **Action Taken by `aircraft-maintenance-service`**:
  1. Increments the aircraft's cumulative flight hours (`flightHours += durationHours`).
  2. Transitions aircraft status from `IN_FLIGHT` back to `AVAILABLE`.
  3. Updates persistent CQRS Mood Projection in DB (`aircraft_mood_views`).

#### Expected JSON Payload:
```json
{
  "eventId": "7c8d9e0f-1a2b-3c4d-5e6f-7a8b9c0d1e2f",
  "eventType": "FlightCompletedEvent",
  "occurredAt": "2026-11-20T14:45:00Z",
  "flightId": 1054,
  "flightNumber": "TP1924",
  "registration": "CS-TPA",
  "originAirportIata": "LIS",
  "destinationAirportIata": "OPO",
  "durationHours": 0.85
}
```
*(Note: both `registration` and `aircraftRegistration` are supported).*

---

## 4. Dead Letter Exchange (DLX) & Retry Policy

To avoid infinite redelivery loops on transient errors:
1. Every consumer queue configures:
   - `x-dead-letter-exchange: aisafe.dlx`
   - `x-dead-letter-routing-key: aircraft.events.dlq`
2. Spring AMQP listener retry:
   - `spring.rabbitmq.listener.simple.retry.enabled=true`
   - `spring.rabbitmq.listener.simple.retry.max-attempts=3`
   - `spring.rabbitmq.listener.simple.default-requeue-rejected=false`
3. After 3 failed delivery attempts, the poison message is rejected without requeue and routed directly into `aircraft.events.dlq` for operator inspection.
