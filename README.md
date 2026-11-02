# Temporal Workflow Engine

A practical project for learning and exploring **Temporal Workflow Engine** with Java and Spring Boot.

This repository demonstrates how to build durable, fault-tolerant, and reliable workflows using Temporal. It focuses on workflow orchestration, activity execution, event history, persistence, retry mechanisms, and failure recovery.

## 1. What is Temporal?

[Temporal](https://temporal.io/) is an open-source workflow orchestration platform that enables developers to build reliable applications by managing long-running and distributed processes.

Temporal provides **Durable Execution**, ensuring that workflow execution can resume from its previous state even when workers, services, or infrastructure fail.

Instead of manually implementing state management, retry mechanisms, timeout handling, and failure recovery, developers can delegate these responsibilities to Temporal.

### Key Features

- **Durable Execution:** Preserve workflow progress across failures.
- **Workflow Orchestration:** Coordinate multiple activities and services.
- **Automatic Retry:** Retry failed activities based on configurable policies.
- **Timeout Management:** Detect stalled or unresponsive activities.
- **Event History:** Record workflow execution events.
- **Replay:** Reconstruct workflow state from event history.
- **Signal & Query:** Interact with running workflows.
- **Scalability:** Distribute workflow and activity execution across workers.

## 2. Architecture Overview

Temporal follows a server-worker architecture.

```text
                 Application
                      |
                      v
               Temporal SDK
                      |
                      v
              Temporal Server
                      |
          +-----------+-----------+
          |                       |
          v                       v
     Persistence              Task Queue
          |                       |
          v                       v
      PostgreSQL              Worker
                                  |
                       +----------+----------+
                       |                     |
                       v                     v
                  Workflow              Activity
                  Execution             Execution
```

### Main Components

| Component | Responsibility |
|---|---|
| Temporal Client | Communicates with Temporal Server and starts or interacts with workflows. |
| Temporal Server | Manages workflow executions, task dispatching, timers, and persistence. |
| Worker | Polls task queues and executes workflow and activity code. |
| Workflow | Defines business orchestration and execution logic. |
| Activity | Performs external operations such as database access, API calls, and file processing. |
| Task Queue | Distributes workflow and activity tasks to available workers. |
| Persistence | Stores workflow state, event history, and execution metadata. |

## 3. Workflow vs Activity

Temporal separates orchestration logic from external operations.

### Workflow

A Workflow defines the execution sequence and business process.

Responsibilities:
- Coordinate activities.
- Manage workflow state.
- Handle activity failures.
- Implement retry and timeout strategies.
- Wait for signals, timers, or external events.

Example:

```java
@WorkflowInterface
public interface OrderWorkflow {

    @WorkflowMethod
    void processOrder(String orderId);
}
```

### Activity

An Activity performs operations that interact with external systems.

Responsibilities:
- Database operations.
- REST API calls.
- Payment processing.
- File processing.
- Sending emails or notifications.

Example:

```java
@ActivityInterface
public interface OrderActivity {

    void validateOrder(String orderId);

    void processPayment(String orderId);

    void shipOrder(String orderId);
}
```

### Example Workflow Implementation

```java
public class OrderWorkflowImpl implements OrderWorkflow {

    private final OrderActivity activity =
        Workflow.newActivityStub(
            OrderActivity.class,
            ActivityOptions.newBuilder()
                .setStartToCloseTimeout(
                    Duration.ofSeconds(30)
                )
                .build()
        );

    @Override
    public void processOrder(String orderId) {

        activity.validateOrder(orderId);

        activity.processPayment(orderId);

        activity.shipOrder(orderId);
    }
}
```

## 4. Event History

Event History is one of the most important mechanisms in Temporal.

It records significant events throughout a Workflow Execution lifecycle.

Examples:

| Event Type | Description |
|---|---|
| WorkflowExecutionStarted | Workflow execution started. |
| WorkflowTaskScheduled | A workflow task was scheduled. |
| WorkflowTaskStarted | A worker started processing a workflow task. |
| WorkflowTaskCompleted | Workflow task completed. |
| ActivityTaskScheduled | An activity was scheduled. |
| ActivityTaskStarted | An activity started executing. |
| ActivityTaskCompleted | An activity completed successfully. |
| ActivityTaskFailed | An activity failed. |
| WorkflowExecutionCompleted | Workflow execution completed. |
| WorkflowExecutionFailed | Workflow execution failed. |

### What does Event History store?

- Workflow input and execution metadata.
- Activity scheduling information.
- Activity input and result payloads.
- Activity failures and timeout information.
- Timer events.
- Workflow signals.
- Workflow completion results.

Event History is persisted in Temporal's persistence database.

### Event History and Replay

Temporal uses Event History to reconstruct workflow state.

When a worker restarts or a workflow task needs to be processed again, Temporal replays the workflow code against the recorded event history.

```text
       Event History
             |
             v
       Workflow Replay
             |
             v
    Reconstruct Workflow State
             |
             v
      Continue Execution
```

Important:
- Workflow code is replayed.
- Previously completed activities are not executed again during replay.
- Their recorded results are returned from Event History.
- Workflow code must be deterministic to support replay.

## 5. Persistence with PostgreSQL

Temporal requires a persistence database to store workflow execution information.

This project uses PostgreSQL as its persistence backend.

### Main Persistence Concepts

| Concept | Description |
|---|---|
| Mutable State | Stores the current execution state and information required to coordinate workflow processing. |
| Event History | Stores the sequence of workflow execution events. |
| Task Queues | Persist tasks that need to be dispatched and processed. |
| Visibility | Supports listing and searching workflow executions. |
| Namespace Metadata | Stores namespace-related configuration and metadata. |

### Persistence Architecture

```text
PostgreSQL
    |
    +-- Workflow Execution State
    |
    +-- Event History
    |
    +-- Task Persistence
    |
    +-- Visibility Data
    |
    +-- Namespace Metadata
```

Temporal manages its persistence schema automatically when using the `temporalio/auto-setup` Docker image.

PostgreSQL is used for Temporal internal persistence, not as a replacement for the application's business database.

## 6. Activity Failure Detection

Temporal provides mechanisms to detect activity failures and recover from them.

### Failure Detection Process

```text
Workflow
    |
    v
Execute Activity
    |
    v
Activity Failure
    |
    v
Temporal Server
    |
    v
Check Retry Policy
    |
    +---- Retry Available
    |          |
    |          v
    |     Retry Activity
    |
    +---- Retry Exhausted
               |
               v
        ActivityFailure
               |
               v
       Workflow Handles Error
```

### Retry Policy

Temporal supports configurable retry policies.

Example:

```java
RetryOptions retryOptions =
    RetryOptions.newBuilder()
        .setInitialInterval(Duration.ofSeconds(2))
        .setBackoffCoefficient(2.0)
        .setMaximumInterval(Duration.ofSeconds(10))
        .setMaximumAttempts(3)
        .build();
```

| Configuration | Description |
|---|---|
| Initial Interval | Initial delay before retrying. |
| Backoff Coefficient | Multiplier used to increase retry intervals. |
| Maximum Interval | Maximum retry delay. |
| Maximum Attempts | Maximum number of execution attempts, including the initial attempt. |
| Do Not Retry | Exception types that should not be retried. |

### Non-Retryable Failure

Some failures should not be retried, such as invalid business input.

```java
throw ApplicationFailure.newNonRetryableFailure(
    "User does not exist",
    "USER_NOT_FOUND"
);
```

## 7. Activity Timeouts

Temporal provides four main timeout types.

| Timeout | Description |
|---|---|
| Schedule-To-Start | Maximum time an activity can wait in the task queue before a worker starts it. |
| Start-To-Close | Maximum duration of a single activity execution attempt. |
| Schedule-To-Close | Maximum total duration of an activity execution, including retries and backoff. |
| Heartbeat | Maximum interval between heartbeat signals from a running activity. |

Example:

```java
ActivityOptions options =
    ActivityOptions.newBuilder()
        .setStartToCloseTimeout(Duration.ofSeconds(30))
        .setScheduleToCloseTimeout(Duration.ofMinutes(3))
        .setHeartbeatTimeout(Duration.ofSeconds(30))
        .build();
```

## 8. Activity Heartbeat

Heartbeat is useful for long-running activities.

For example, when processing a large Excel file, the activity can periodically report its progress.

```java
public class ImportActivityImpl implements ImportActivity {

    @Override
    public void importExcel(String filePath) {

        ActivityExecutionContext context =
            Activity.getExecutionContext();

        for (int i = 0; i < 100000; i++) {

            // Process row

            if (i % 1000 == 0) {
                context.heartbeat(i);
            }
        }
    }
}
```

Heartbeat details can be used to resume processing from the last reported checkpoint after an activity failure and retry.

## 9. Worker and Task Queue

A Worker executes workflow and activity tasks received from a Task Queue.

Multiple workflow implementations can be registered on the same Worker.

Example:

```java
WorkerFactory factory =
    WorkerFactory.newInstance(client);

Worker worker =
    factory.newWorker("BUSINESS_TASK_QUEUE");

worker.registerWorkflowImplementationTypes(
    OrderWorkflowImpl.class,
    PaymentWorkflowImpl.class
);

worker.registerActivitiesImplementations(
    new OrderActivityImpl(),
    new PaymentActivityImpl()
);

factory.start();
```

A WorkerFactory can manage multiple Workers, and each Worker can use a different Task Queue.

Separate task queues are useful when different workflows require independent scaling or resource allocation.

## 10. Docker Environment

This project uses Docker Compose to run Temporal Server, PostgreSQL, and Temporal Web UI.

### Docker Compose

```yaml
services:

  temporal:
    image: temporalio/auto-setup:1.21.5
    ports:
      - "7233:7233"
    environment:
      - DB=postgresql
      - DB_PORT=5432
      - POSTGRES_USER=temporal
      - POSTGRES_PWD=temporal
      - POSTGRES_SEEDS=postgres
    depends_on:
      - postgres

  postgres:
    image: postgres:13
    environment:
      - POSTGRES_USER=temporal
      - POSTGRES_PASSWORD=temporal
    ports:
      - "5432:5432"

  temporal-ui:
    image: temporalio/ui:latest
    ports:
      - "8088:8080"
    environment:
      - TEMPORAL_ADDRESS=temporal:7233
    depends_on:
      - temporal
```

### Service Endpoints

| Service | Endpoint |
|---|---|
| Temporal Server | localhost:7233 |
| Temporal Web UI | http://localhost:8088 |
| PostgreSQL | localhost:5432 |

### Start Services

```bash
docker compose up -d
```

Check running containers:

```bash
docker compose ps
```

View Temporal Server logs:

```bash
docker compose logs -f temporal
```

Stop services:

```bash
docker compose down
```

**Note:** The above Docker Compose configuration is a learning setup. For persistent local data, configure a PostgreSQL volume. For production, use health checks, secure credentials, compatible pinned image versions, and appropriate persistence and visibility database configuration.

## 11. Monitoring Workflow Execution

Temporal Web UI allows developers to inspect workflow executions.

Features include:

- View running and completed workflows.
- Inspect workflow execution details.
- Examine Event History.
- Inspect activity execution status.
- Review activity failures and retries.
- Inspect workflow input and output.

Open:

```text
http://localhost:8088
```

Select a Namespace and Workflow Execution to inspect its Event History.

## 12. Important Design Considerations

### Deterministic Workflow Code

Workflow code must be deterministic.

Avoid directly using:
- System.currentTimeMillis()
- Random number generation.
- Direct database access.
- Direct HTTP requests.
- Uncontrolled thread creation.

Use Temporal workflow APIs and Activities for operations that interact with external systems.

### Idempotency

Activities may execute more than once due to retries or uncertain completion.

External operations such as payment processing should implement idempotency to prevent duplicate side effects.

### Event History Size

Event History grows as a workflow executes.

Avoid passing excessively large payloads between workflows and activities. Store large files in external storage and pass references instead.

For long-running workflows, consider Continue-As-New to keep individual execution histories manageable.

### Business Data vs Temporal Persistence

Temporal persistence should not replace the application's business database.

Recommended separation:

```text
Application Database
    |
    +-- Orders
    +-- Customers
    +-- Payments

Temporal Database
    |
    +-- Workflow State
    +-- Event History
    +-- Task Persistence
```

## 13. Learning Roadmap

Topics explored in this project:

- [x] Temporal Architecture
- [x] Workflow and Activity
- [x] Worker and Task Queue
- [x] Event History
- [x] PostgreSQL Persistence
- [x] Workflow Replay
- [x] Activity Failure Detection
- [x] Retry Policy
- [x] Activity Timeouts
- [x] Activity Heartbeat
- [ ] Workflow Signals and Queries
- [ ] Child Workflows
- [ ] Workflow Versioning
- [ ] Continue-As-New
- [ ] Saga Pattern
- [ ] Workflow Cancellation
- [ ] Temporal Testing

## 14. References

- [Temporal Official Documentation](https://docs.temporal.io/)
- [Temporal Java SDK](https://github.com/temporalio/sdk-java)
- [Temporal Server](https://github.com/temporalio/temporal)
- [Temporal Docker Samples](https://github.com/temporalio/docker-compose)
- [Temporal Samples](https://github.com/temporalio/samples-java)

---

**Purpose:** Hands-on learning and experimentation with Temporal Workflow Engine, focusing on reliable distributed workflow orchestration and fault-tolerant application design.
