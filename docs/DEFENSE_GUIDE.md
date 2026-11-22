# Guia de Apresentação e Defesa do Projeto (SIDIS - Sistemas Distribuídos)

Este documento foi desenhado para te dar todas as respostas técnicas, fundamentadas e estruturadas para responderes com total confiança às perguntas do professor durante a avaliação.

---

## 1. Como justificar a decomposição do Monólito usando DDD (Domain-Driven Design)?

O professor pergunta com frequência: *"Porque dividiram o sistema desta forma? Onde está o DDD?"*

### A Resposta Fundamental:
> *"Aplicámos o padrão **Domain-Driven Segregation**. Não dividimos o sistema por camadas técnicas (UI, Business, Data), mas sim por **Bounded Contexts** (Contextos Delimitados), respeitando a coesão do modelo de domínio original e garantindo que cada microsserviço tem o seu próprio ciclo de vida e base de dados isolada."*

### Os 3 Bounded Contexts do Projeto:

| Bounded Context / Microsserviço | Responsabilidade do Domínio | Agregados / Entidades Principais |
| :--- | :--- | :--- |
| **`aircraft-maintenance-service`** | Gestão do ciclo de vida da frota física e registo de intervenções técnicas. | **Aggregate Root:** `Aircraft`<br>**Aggregate Root:** `MaintenanceRecord`<br>**Child Entity / VO:** `ChecklistItem`<br>**Lookup:** `AircraftModel`, `MaintenanceTemplate` |
| **`airports-routes-service`** | Infraestrutura estática e rede geográfica de voos. | **Aggregate Root:** `Airport` (com pistas e certificações)<br>**Aggregate Root:** `FlightRoute` (com distâncias e requisitos operacionais) |
| **`flight-operations-service`** | Agendamento dinâmico de voos e orquestração operacional. | **Aggregate Root:** `ScheduledFlight`<br>**Locks / Concorrência:** `AircraftBookingLock` |

### Princípios de DDD Aplicados no Código:
1. **Ubiquitous Language (Linguagem Ubíqua):**
   - Todos os termos no código refletem a aviação real e a especificação da ICAO/IATA: `AircraftStatus` (`AVAILABLE`, `UNDER_MAINTENANCE`, `IN_FLIGHT`), `MaintenanceComponent` (`ENGINE`, `AIRFRAME`, `AVIONICS`), `ScheduledFlight`, `ChecklistItem`.
2. **Aggregate Boundaries & Invariantes:**
   - O `MaintenanceRecord` protege a integridade do seu checklist: o checklist é clonado a partir de um `MaintenanceTemplate` no momento da criação e evolui de forma independente dentro do registo.
   - A transição de estado da aeronave é uma regra de negócio invariante: uma aeronave em `UNDER_MAINTENANCE` não pode ser submetida a um novo registo em aberto sem concluir o anterior.
3. **Database per Service:**
   - Nenhum serviço acede à base de dados de outro. Cada microsserviço tem o seu esquema JPA isolado, permitindo escalabilidade e deployments desacoplados.

---

## 2. Como é garantida a Tolerância a Falhas e a questão do SPOF (Single Point of Failure)?

O professor perguntará: *"Se um servidor ou base de dados for abaixo, o sistema para? Onde está o vosso SPOF? E o RabbitMQ?"*

### A Resposta:
> *"Não existe ponto único de falha nas instâncias do serviço (3 cópias, cada uma com a sua base de dados isolada). Ao nível da mensageria assíncrona, reconhecemos que um único container RabbitMQ representa um ponto único de falha de infraestrutura no ambiente local, pelo que implementámos o padrão **Transactional Outbox** para desacoplar as transações da disponibilidade do broker e garantir resiliência."*

1. **Armazenamento Descentralizado:**
   - Cada réplica do microsserviço tem a sua própria base de dados independente (H2 ou PostgreSQL). Se a Réplica 1 for abaixo, as Réplicas 2 e 3 continuam a operar sem qualquer interrupção.
2. **P2P HTTP Forwarding & Prevenção de Loops:**
   - Se um pedido de leitura chega a uma réplica e a aeronave não está na sua partição local, consulta os peers usando JWT (`ROLE_SERVICE`).
   - O cabeçalho `X-Forwarded-From: instance1` previne ciclos infinitos: o peer que recebe nunca reencaminha.
   - **404 Not Found vs. 503 Service Unavailable:**
     - **404 Not Found:** É uma resposta *definitiva* (todas as instâncias ativas responderam afirmativamente que a aeronave não existe).
     - **503 Service Unavailable:** Se um peer não responder (timeout ou conexão recusada), a indisponibilidade deve ser sinalizada como 503, pois em sistemas distribuídos a falha de comunicação impede garantir a inexistência do dado.
3. **Resiliência do Broker RabbitMQ (Transactional Outbox Pattern):**
   - Se o RabbitMQ cair, **o serviço NÃO perde eventos nem falha transações**:
     - O evento é gravado na tabela `outbox_events` na mesma transação da escrita de negócio.
     - O `OutboxDispatcher` retenta periodicamente com backoff exponencial. Quando o RabbitMQ regressar, descarrega todos os eventos pendentes (garantia *at-least-once*).
   - Em produção empresarial, a infraestrutura utilizaria um **Cluster RabbitMQ de 3 nós com Quorum Queues (algoritmo de consenso Raft)** para eliminar o SPOF no próprio broker.

---

## 3. Como é tratada a Consistência e a Concorrência?

O professor perguntará: *"Como lidam com alterações concorrentes e consistência em sistemas distribuídos?"*

### A Resposta:
1. **Consistência Eventual:**
   - As réplicas não bloqueiam em transações distribuídas lentas (como 2-Phase Commit). Favorecemos a **Disponibilidade** e a **Tolerância a Partições** (Teorema CAP), garantindo consistência eventual via sincronização entre réplicas.
2. **Optimistic Locking com `@Version`:**
   - Para prevenir o problema de **Lost Updates** (dois técnicos ou operadores a atualizar o mesmo registo ao mesmo tempo), usamos locking otimista em JPA:
   - Presente em `Aircraft.java`, `MaintenanceRecord.java` e `MaintenanceTemplate.java`.
   - Se dois pedidos tentarem atualizar o mesmo registo com a mesma versão, um passa e o outro recebe HTTP `409 Conflict`, sendo tratado pelo `GlobalExceptionHandler.java`.

---

## 4. Como funciona a Segurança e a Comunicação Inter-serviços?

O professor perguntará: *"Qualquer utilizador pode chamar qualquer endpoint? Como comunicam os serviços entre si?"*

### A Resposta:
1. **Separação Clara de Tráfego:**
   - **Endpoints Públicos/Utilizador (`/api/**`):** Acessíveis por humanos autenticados com JWT que possuem as roles de negócio atribuídas:
     - `MAINTENANCE_TECHNICIAN`: Abrir ordens, iniciar e completar manutenções.
     - `MAINTENANCE_SUPERVISOR`: Visão de atividades em curso e tempos médios de resposta.
     - `ATCC`: Relatórios de custos e horas totais da frota.
   - **Endpoints Internos Inter-serviços (`/internal/**`):**
     - O `InternalAircraftController.java` serve dados rápidos necessários a outros serviços (ex: capacidade ativa, alcance máximo, modelo).
     - **Regra de Segurança Estrita:** No `SecurityConfig.java`, a rota `/internal/**` exige obrigatoriamente a role `ROLE_SERVICE`. Nenhum utilizador comum (nem mesmo um técnico ou admin) consegue aceder sem o token de serviço.
2. **Trilha de Auditoria (Audit Logging):**
   - O `AuditLogFilter.java` interceta cada pedido HTTP e regista: utilizador, roles, método HTTP, URI chamado, código de resposta HTTP e IP de origem.

---

## 5. Como demonstrar o projeto em 2 minutos ao professor:

1. **Mostrar os Testes Automatizados no Terminal:**
   ```bash
   cd aircraft-maintenance-service
   JAVA_HOME=/home/andrepm2004/.jdks/corretto-21.0.12.1 ./mvnw test -q
   ```
   *(Mostrar que todos os 163 testes automatizados passam com 0 falhas).*

2. **Mostrar a Coleção do Postman:**
   ```bash
   npx -y newman run postman/aircraft-maintenance.postman_collection.json -e postman/local.postman_environment.json
   ```

3. **Abrir a Documentação OpenAPI (Swagger):**
   - Abrir no browser: `http://localhost:8081/swagger-ui.html`

---

## 6. Assignment 2: Como funciona o CQRS no microsserviço?

O professor perguntará com certeza: *"Como implementaram o CQRS no vosso serviço?"*

### A Resposta Fundamental:
> *"No **Assignment 2**, adotámos o padrão **CQRS (Command-Query Responsibility Segregation)** para segregar as operações de escrita (Commands) das operações de leitura (Queries).*
> 
> *No lado da escrita (**Write Side**), os controllers REST não modificam entidades de forma direta nem anémica; emitem Commands explícitos processados por Command Handlers transacionais (`AircraftCommandHandler` e `MaintenanceCommandHandler`), que validam regras de negócio, asseguram controlo de concorrência otimista (`@Version`) e geram Eventos de Domínio (`AircraftCreatedEvent`, `AircraftStatusChangedEvent`, `MaintenanceScheduledEvent`, `MaintenanceCompletedEvent`).*
> 
> *No lado da leitura (**Read Side**), as consultas operacionais são servidas de forma limpa e sem contenção de locks transacionais, garantindo alto desempenho."*

* **Write Side (Commands & Command Handlers):**
  - Os controllers de escrita (`AircraftController` e `MaintenanceRecordController`) invocam **diretamente os Command Handlers**:
    - `AircraftCommandHandler`: `RegisterAircraftCommand`, `UpdateAircraftStatusCommand`.
    - `MaintenanceCommandHandler`: `ScheduleMaintenanceCommand`, `StartMaintenanceCommand`, `CompleteMaintenanceCommand`, `CancelMaintenanceCommand`.
  - O fluxo de escrita é 100% auditável através do padrão Command, validando regras de negócio e versionamento otimista (`@Version`).
* **Domain Events & Transactional Outbox:**
  - `AircraftStatusChangedEvent`, `AircraftCreatedEvent`, `MaintenanceScheduledEvent`, `MaintenanceCompletedEvent`.
  - Os eventos são persistidos na mesma transação atómica na tabela `outbox_events` (Transactional Outbox) e publicados assincronamente para o RabbitMQ com publisher confirms.

---

## 7. Assignment 2: Como funciona a Mensageria Assíncrona com RabbitMQ (AMQP)?

O professor perguntará: *"Porque usam RabbitMQ e como comunicam os serviços entre si de forma assíncrona?"*

### A Resposta Fundamental:
> *"No Assignment 1, toda a comunicação era síncrona via HTTP/REST. No **Assignment 2**, adicionámos o **RabbitMQ** como Message Broker (AMQP) para desacoplar temporalmente os serviços e permitir integração baseada em eventos (Event-Driven Architecture).*
> 
> *Configurámos uma Topic Exchange chamada `aisafe.events`. Sempre que o estado de uma aeronave muda no nosso serviço (ex: entra em manutenção ou é concluída uma revisão), publicamos um evento assíncrono com a routing key apropriada (`aircraft.status.changed`, `aircraft.maintenance.completed`). O `flight-operations-service` subscreve estes eventos para saber em tempo real que o avião está indisponível para voos, sem necessidade de fazer polling contínuo via HTTP.*
> 
> *Implementámos quatro pilares de resiliência recomendados pelas boas práticas de sistemas distribuídos:
> 1. **Publish/Subscribe por réplica:** Fila dedicada por réplica (`aircraft.events.<instance>`), garantindo que todas as instâncias recebem os eventos de voo (`flight.#`) e eventos de aeronave (`aircraft.#`) para sincronização da projeção CQRS de humores entre réplicas.
> 2. **Isolamento de DLX e DLQ:** Exchange `aisafe.dlx` e fila `aircraft.events.dlq`, com routing key explícita `aircraft.events.dlq` (não poluída com mensagens de outros serviços).
> 3. **Tratamento de Envenenamento e Retries:** Mensagens com JSON inválido são rejeitadas de imediato para a DLQ (`AmqpRejectAndDontRequeueException`). Falhas transitórias beneficiam de 3 retentativas automáticas antes de caírem na DLQ.
> 4. **Consumo Idempotente:** Registo atómico de `eventId` na tabela `processed_events`, impedindo acumulação duplicada de horas de voo em caso de redelivery.
> 5. **Transactional Outbox Pattern:** Garante envio fiável 'at-least-once' e elimina 'phantom events' em caso de rollback."*
