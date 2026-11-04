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

## 2. Como é garantida a Tolerância a Falhas e a ausência de SPOF (Single Point of Failure)?

O professor perguntará: *"Se um servidor ou base de dados for abaixo, o sistema para? Onde está o vosso SPOF?"*

### A Resposta:
> *"A nossa arquitetura tem **Zero SPOF** graças à replicação P2P (Peer-to-Peer) e ao isolamento de armazenamento por réplica."*

1. **Armazenamento Descentralizado:**
   - Cada réplica do microsserviço tem a sua própria instância de base de dados (em memória H2 ou PostgreSQL independente). Não existe uma base de dados única central que, ao falhar, deite o sistema abaixo.
2. **P2P HTTP Forwarding & Prevenção de Loops (Leituras Resilientes):**
   - Implementado em `HttpPeerClient.java` (que implementa `PeerClient.java`) e coordenado por `ClusterConfig.java`.
   - Se um pedido de leitura (`GET`) chega à Réplica 1 e a aeronave não existe localmente (cache miss), a Réplica 1 consulta a sua lista de peers (`PEERS=http://localhost:8091,...`) usando autenticação JWT com a role `SERVICE`.
   - **Prevenção de Loops:** O pedido para o peer inclui o cabeçalho `X-Forwarded-From: instance1`. Se a Réplica 2 também não tiver o dado, ao detetar esse cabeçalho responde `404` imediatamente e **nunca** volta a encaminhar, prevenindo ciclos infinitos.
   - **Rastreabilidade de Dados:** O cabeçalho `X-Data-Source: local` ou `peer` comprova ao cliente de onde veio a resposta.
   - **Tolerância a nós caídos:** Se um peer estiver offline ou der timeout (1500ms), a exceção é capturada com aviso e a execução continua para os restantes peers sem interromper o serviço.

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
   *(Mostrar que todos os 161 testes automatizados passam com 0 falhas).*

2. **Mostrar a Coleção do Postman (incluindo o endpoint `/api/v2/unicorns`):**
   ```bash
   npx -y newman run postman/aircraft-maintenance.postman_collection.json -e postman/local.postman_environment.json
   ```

3. **Abrir a Documentação OpenAPI (Swagger):**
   - Abrir no browser: `http://localhost:8081/swagger-ui.html`

---

## 6. Assignment 2: Como funciona o CQRS e como o endpoint `/api/v2/unicorns` se integra no padrão?

O professor perguntará com certeza: *"Como implementaram o CQRS e como é que o endpoint dos unicórnios se integra nisso?"*

### A Resposta Fundamental:
> *"No **Assignment 2**, adotámos o padrão **CQRS (Command-Query Responsibility Segregation)** para segregar as operações de escrita (Commands) das operações de leitura e projeções analíticas (Queries).*
> 
> *O endpoint `/api/v2/unicorns` é um exemplo puro de uma **Projeção de Leitura (Read Model)** do CQRS: não faz consultas à entidade transacional `Aircraft` nem efetua joins relacionais pesados. Em vez disso, consulta diretamente a `AircraftMoodProjection`, que projeta em tempo real o estado de 'humor' das aeronaves com base nos eventos de domínio (`AircraftStatusChangedEvent`, `MaintenanceCompletedEvent`, etc.).*
> 
> *Desta forma, garantimos que leituras de monitorização e dashboards nunca bloqueiam transações de escrita e respeitamos a consistência eventual."*

* **Write Side (Commands):**
  - `RegisterAircraftCommand`, `UpdateAircraftStatusCommand`, `ScheduleMaintenanceCommand`, `StartMaintenanceCommand`, `CompleteMaintenanceCommand`.
  - Processados por `AircraftCommandHandler` e `MaintenanceCommandHandler` com validação de invariantes e persistência transacional.
* **Domain Events:**
  - `AircraftStatusChangedEvent`, `AircraftCreatedEvent`, `MaintenanceScheduledEvent`, `MaintenanceCompletedEvent`.
  - Publicados via `DomainEventPublisher` tanto localmente para projeções CQRS como para o RabbitMQ.
* **Read Side (Queries / Projeções):**
  - `GetAircraftMoodQuery` processada por `AircraftQueryHandler`.
  - `AircraftMoodProjection` atualiza reativamente a view `AircraftMoodView` (`mood`, `energyLevel`, `sparkleFactor`, `statusMessage`).

---

## 7. Assignment 2: Como funciona a Mensageria Assíncrona com RabbitMQ (AMQP)?

O professor perguntará: *"Porque usam RabbitMQ e como comunicam os serviços entre si de forma assíncrona?"*

### A Resposta Fundamental:
> *"No Assignment 1, toda a comunicação era síncrona via HTTP/REST. No **Assignment 2**, adicionámos o **RabbitMQ** como Message Broker (AMQP) para desacoplar temporalmente os serviços e permitir integração baseada em eventos (Event-Driven Architecture).*
> 
> *Configurámos uma Topic Exchange chamada `aisafe.events`. Sempre que o estado de uma aeronave muda no nosso serviço (ex: entra em manutenção ou é concluída uma revisão), publicamos um evento assíncrono com a routing key apropriada (`aircraft.status.changed`, `aircraft.maintenance.completed`). O `flight-operations-service` subscreve estes eventos para saber em tempo real que o avião está indisponível para voos, sem necessidade de fazer polling contínuo via HTTP.*
> 
> *Além disso, implementámos **degradação graciosa (Fault Tolerance)**: se o broker RabbitMQ estiver temporariamente offline, as operações de escrita locais continuam a ser processadas com sucesso, registando um aviso estruturado nos logs sem quebrar o cliente."*
