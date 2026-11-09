# AISafe Inter-Service AMQP Contract Specification
**Course**: Sistemas Distribuídos (LETI-SIDIS 2026/27)  
**Broker**: RabbitMQ 3 (`rabbitmq:3-management`)  
**Exchange**: `aisafe.events` (Topic Exchange, durable)  
**Serialization**: JSON (`application/json`) via Spring AMQP `Jackson2JsonMessageConverter`

---

## 1. Overview & Broker Topology

Inter-service asynchronous communication in Assignment 2 is decoupled using a topic exchange pattern. Microservices publish domain events to the exchange `aisafe.events` with hierarchical routing keys. Other microservices bind private queues to the exchange with matching patterns.

```text
               +----------------------------------+
               | RabbitMQ Topic: "aisafe.events"  |
               +----------------------------------+
                     /                      \
   aircraft.#       /                        \  flight.#
                   v                          v
      +-------------------------+      +-------------------------+
      |  aircraft.events.queue  |      |   flight.events.queue   |
      | (aircraft-maintenance)  |      |   (flight-operations)   |
      +-------------------------+      +-------------------------+
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
| **Management UI** | `http://localhost:15672` | (Login: `guest` / `guest`) |

---

## 2. Events Published by `aircraft-maintenance-service`

All events include standard metadata (`eventId`, `eventType`, `occurredAt`).

### 2.1 `AircraftStatusChangedEvent`
* **Routing Key**: `aircraft.status.changed`
* **Triggered When**: An aircraft transitions between states (`AVAILABLE`, `IN_FLIGHT`, `UNDER_MAINTENANCE`, `AOG`).
* **Intended Consumer**: `flight-operations-service` (to know if an aircraft is airworthy or grounded).

#### JSON Schema & Example:
```json
{
  "eventId": "3c90f28e-5b1b-4f9e-a89e-b7d14e366112",
  "eventType": "AircraftStatusChangedEvent",
  "occurredAt": "2026-11-09T11:30:00Z",
  "registration": "CS-TPA",
  "newStatus": "UNDER_MAINTENANCE",
  "previousStatus": "AVAILABLE"
}
```

---

### 2.2 `AircraftCreatedEvent`
* **Routing Key**: `aircraft.created`
* **Triggered When**: A new aircraft instance is registered in the fleet.
* **Intended Consumers**: `flight-operations-service`, `airports-routes-service`.

#### JSON Schema & Example:
```json
{
  "eventId": "a7b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
  "eventType": "AircraftCreatedEvent",
  "occurredAt": "2026-11-09T11:30:00Z",
  "registration": "CS-TPA",
  "model": "Airbus A320neo",
  "status": "AVAILABLE",
  "flightHours": 0.0
}
```

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
  "occurredAt": "2026-11-09T11:30:00Z",
  "recordId": 1,
  "registration": "CS-TPA",
  "templateType": "DAILY_INSPECTION",
  "scheduledDate": "2026-11-10T08:00:00Z"
}
```

---

### 2.4 `MaintenanceCompletedEvent`
* **Routing Key**: `aircraft.maintenance.completed`
* **Triggered When**: A maintenance technician finishes an inspection and approves return to service.
* **Intended Consumer**: `flight-operations-service` (unblocks the aircraft for flight scheduling).

#### JSON Schema & Example:
```json
{
  "eventId": "5e6f7a8b-9c0d-1e2f-3a4b-5c6d7e8f9a0b",
  "eventType": "MaintenanceCompletedEvent",
  "occurredAt": "2026-11-09T11:30:00Z",
  "recordId": 1,
  "registration": "CS-TPA",
  "loggedHours": 3.5,
  "totalCost": 450.00
}
```

---

## 3. Events Consumed by `aircraft-maintenance-service`

`aircraft-maintenance-service` binds queue `aircraft.events.queue` with routing pattern `flight.#` to consume flight lifecycle events.

### 3.1 `FlightCompletedEvent`
* **Expected Routing Key**: `flight.completed`
* **Publisher**: `flight-operations-service`
* **Action Taken by `aircraft-maintenance-service`**:
  1. Increments the aircraft's cumulative flight hours (`flightHours += durationHours`).
  2. Transitions aircraft status from `IN_FLIGHT` back to `AVAILABLE`.
  3. Reactively triggers CQRS Mood Projection update (`mood: HAPPY`, `sparkleFactor` recalculated).

#### Expected JSON Payload:
```json
{
  "eventId": "7c8d9e0f-1a2b-3c4d-5e6f-7a8b9c0d1e2f",
  "eventType": "FlightCompletedEvent",
  "occurredAt": "2026-11-09T14:45:00Z",
  "flightId": 1054,
  "flightNumber": "TP1924",
  "registration": "CS-TPA",
  "originAirportIata": "LIS",
  "destinationAirportIata": "OPO",
  "durationHours": 0.85
}
```

---

## 4. Integration Guide for Sibling Services (Spring Boot AMQP)

### Step 1: Add Dependency in `pom.xml`
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

### Step 2: Configure Jackson JSON Converter in Spring Configuration
```java
@Configuration
public class RabbitConfig {
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
```

### Step 3: Publishing an Event from `flight-operations-service`
```java
@Service
public class FlightDispatcherService {
    private final RabbitTemplate rabbitTemplate;

    public FlightDispatcherService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void completeFlight(FlightCompletedEvent event) {
        rabbitTemplate.convertAndSend("aisafe.events", "flight.completed", event);
    }
}
```

### Step 4: Subscribing to Aircraft Status Changes in `flight-operations-service`
```java
@RabbitListener(queues = "flight.aircraft.status.queue")
public void handleAircraftStatus(AircraftStatusChangedEvent event) {
    if ("UNDER_MAINTENANCE".equals(event.newStatus()) || "AOG".equals(event.newStatus())) {
        // Ground aircraft or prevent assignment
    }
}
```
