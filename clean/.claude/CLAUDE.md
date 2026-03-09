# Clean Architecture Kotlin Project

## Project Purpose

Spring Boot application demonstrating Clean Architecture + Kotlin + Arrow-kt.

## Project Overview

Spring Boot application structured in Clean Architecture with 5 Gradle modules and **CQRS** (Command Query Responsibility Segregation):

| Module           | Package                          | Role                                                                |
|------------------|----------------------------------|---------------------------------------------------------------------|
| `domain`         | `*.domain`                       | Entities, Value Objects, Domain Services, Repository interfaces (command only), Domain errors |
| `application`    | `*.application`                  | Use cases split into `command/` and `query/` sub-packages           |
| `infrastructure` | `*.infrastructure`               | Repository implementations (jOOQ + JDBC), query repository impls, Port adapters |
| `presentation`   | `*.presentation`                 | REST controllers, DTOs                                              |
| `framework`      | `*.framework`                    | Spring Boot entry point, DI configuration                           |

### CQRS Pattern

The application layer is split into **command** and **query** sides:

```
application/
├── command/            # Write operations (go through domain layer)
│   ├── dto/            # Input DTOs that map to domain entities
│   ├── error/          # Command-specific application errors
│   ├── port/           # Command-side port interfaces
│   └── usecase/        # Command use cases
├── query/              # Read operations (bypass domain layer)
│   ├── dto/            # Query DTOs (flat data, primitives only — no domain types)
│   ├── error/          # Query-specific application errors
│   ├── port/           # Query-side port interfaces (if needed)
│   ├── repository/     # Query repository interfaces (defined HERE, not in domain)
│   └── usecase/        # Query use cases
├── error/              # Shared base (ApplicationError)
└── port/               # Shared port interfaces (ClockPort, TransactionPort)
```

Infrastructure layer mirrors the command/query split:

```
infrastructure/
├── command/
│   ├── adapter/        # Command-side port implementations
│   └── repository/     # Domain repository implementations (write-only)
└── query/
    ├── adapter/        # Query-side port implementations (if needed)
    └── repository/     # Query repository implementations (read-only)
```

**Key CQRS rules:**
- **Command side**: Flows through domain layer (`use case → domain repository → domain entity`). Domain repository interface lives in `domain/`. Command-side port interfaces live in `application/command/port/`.
- **Query side**: Bypasses domain layer entirely. Query repository interface lives in `application/query/repository/`. Query-side port interfaces live in `application/query/port/`. Returns flat DTOs (`XxxDto`, `PageDto`) directly — no domain entities or value objects.
- **Domain repository** (`domain/repository/`): Only write methods (`save`, `delete`, etc.). Read methods are removed.
- **Query repository** (`application/query/repository/`): Read-only methods (`findById`, `list`). Returns application-level DTOs, not domain entities.
- **Shared ports** (`application/port/`): Cross-cutting port interfaces used by both sides (e.g., `ClockPort`, `TransactionPort`).
- **Command ports** (`application/command/port/`): Port interfaces used only by command use cases.
- **Query ports** (`application/query/port/`): Port interfaces used only by query use cases (if needed).
- **Port adapters**: Infrastructure implementations of port interfaces. Placed in `infrastructure/command/adapter/` or `infrastructure/query/adapter/` matching the port's side.

## Architecture Rules (MUST follow)

### Module dependency direction

```
framework → presentation → application → domain ← infrastructure
```

- `domain`: no Spring dependencies; depends on `arrow-core` and `slf4j-api`. Contains entities, value objects, domain services (in `domain/service/`), command-side repository interfaces, and domain errors.
- `application`: depends only on `:domain`. No Spring annotations. No `@Component`, no `@Repository`. Contains both command and query sub-packages. Query repository interfaces are defined here (not in domain).
- `infrastructure`: implements domain repository interfaces (command), application query repository interfaces (query), and port adapters (command/query). May use Spring, JDBC, jOOQ, Flyway.
- `presentation`: depends on `:application`. May use Spring MVC. No domain logic.
- `framework`: wires everything together. Owns `@SpringBootApplication` and `UseCaseConfig`.

### `domain/` — FORBIDDEN imports

- `org.springframework.*`
- `jakarta.*` / `javax.*`
- `*.application.*`, `*.infrastructure.*`, `*.presentation.*`, `*.framework.*`
- Any persistence or web framework

Allowed external: `arrow.core.*`, `kotlinx.coroutines.*`, `org.slf4j.*`

### `application/` — FORBIDDEN imports

- `org.springframework.*`
- `jakarta.*` / `javax.*`
- `*.infrastructure.*`, `*.presentation.*`, `*.framework.*`
- Only allowed external: `arrow.core.*`, `kotlinx.coroutines.*`, `org.slf4j.*`

### `presentation/` — FORBIDDEN imports

- `*.infrastructure.*`

> **Note**: `presentation` may import `*.domain.*` (entities, value objects, error types) directly.
> Use cases return domain objects, so presentation needs to reference them for DTO mapping.
> Domain logic must NOT be placed in the presentation layer — use domain objects only for reading/mapping.

### Layer Purity (domain / application)

Do NOT add any new library dependencies to `domain` or `application` modules (`build.gradle.kts`).
These layers must remain pure. The allowed external dependencies are fixed and final:

- `arrow-core`
- `kotlinx-coroutines-core`
- `slf4j-api`

If a use case seems to require a new library, implement it in `infrastructure` and expose only a domain interface.
This is enforced at the import level by the `ForbiddenLayerImport` detekt rule (whitelist-based).

### Explicit violations to never introduce

- Do NOT add `@Component` or `@Service` to use case classes or domain service classes — they are instantiated manually in `UseCaseConfig` / `DomainServiceConfig`
- Do NOT add Spring annotations to domain or application layer classes
- Do NOT let domain layer import from infrastructure or presentation
- Do NOT let application layer import from infrastructure or presentation
- Do NOT use `throw` in `domain`, `application`, or `infrastructure` layers — every error must be expressed as `Either<XxxError, T>`. The return type is the complete error specification; throwing makes errors invisible to callers.
- Do NOT use `throw` in presentation layer business logic (controller methods, DTO mapping). Only Spring exception handler infrastructure (e.g., `ResponseStatusException`) may throw.
- Do NOT return a bare type (non-Either) from any fallible operation in domain, application, or infrastructure — if a function can fail, its return type must be `Either`
- Do NOT use `Any` or `Any?` as an explicit type in any layer (parameters, return types, properties). Use specific types, generics, or sealed interfaces. Exception: `<T : Any>` generic constraints are allowed. Enforced by the `NoExplicitAny` detekt rule.

### Infrastructure Layer — FORBIDDEN patterns

- **NO silent fallback for unknown values** — when mapping DB strings to sealed types/enums,
  NEVER use `else -> SomeDefault`. Always return `Either.Left` with an error for unknown values.
  ```kotlin
  // FORBIDDEN — hides data corruption
  else -> SubscriptionStatus.Active(pauseCountInPeriod = 0)

  // REQUIRED — surfaces the problem
  else -> return SomeError.UnknownStatus(dbValue).left()
  ```

### `!!` Operator Rules in Infrastructure

- `!!` on jOOQ Record fields for NOT NULL columns is ALLOWED (Java interop necessity)
  ```kotlin
  // OK — column is NOT NULL in DDL, jOOQ returns nullable due to Java interop
  val name = record.name!!
  val amount = record.amount!!
  ```
- `!!` on NULLABLE columns or business logic values is FORBIDDEN — use `ensureNotNull()` inside `either {}`
  ```kotlin
  // FORBIDDEN — column is NULLABLE, !! hides potential null
  val pausedAt = record.pausedAt!!

  // REQUIRED — handle nullable columns explicitly
  val pausedAt = ensureNotNull(record.pausedAt) { SomeError.MissingField("paused_at") }
  ```
- Rule of thumb: if the DB column has `NOT NULL` constraint → `!!` is fine.
  If the column is nullable → `ensureNotNull()` with proper error.

### Framework Layer — ComponentScan Completeness (CRITICAL)

- `@ComponentScan` in `Application.kt` MUST include ALL module packages that contain Spring-managed beans:
  ```kotlin
  @ComponentScan(
      basePackages = [
          "com.wakita181009.clean.infrastructure",
          "com.wakita181009.clean.presentation",  // MUST include
          "com.wakita181009.clean.framework",
      ],
  )
  ```
- Missing a package means all `@RestController`, `@Repository`, `@Component` beans in that package
  will NOT be registered — the application will start but endpoints/adapters will be silently absent.

### ID Generation Pattern

- When creating a new entity that will receive a DB-generated ID, use nullable ID:
  ```kotlin
  data class Subscription(
      val id: SubscriptionId?,  // null before persistence
      ...
  )
  ```
- Do NOT use `unsafeOf(0L)` as a placeholder — this violates the value object's own invariant (`value > 0`).
- The repository's `save()` returns the entity with the assigned ID from the DB.

### CQRS: How Command Use Cases Read Data

- Domain repositories (`domain/repository/`) contain **write methods only** (`save`, `delete`)
- When a command use case needs to READ data (e.g., find subscription by ID before updating):
  - Define a **command-side query port** in `application/command/port/`
    (e.g., `SubscriptionCommandQueryPort.findById(): Either<Error, Subscription>`)
  - Infrastructure implements this port alongside the domain repository
- Do NOT add `findById` or other read methods to domain repositories

## Tech Stack

- **Language**: Kotlin (JVM)
- **Framework**: Spring Boot
- **Error handling**: Arrow-kt (`Either<Error, Result>`) — use throughout all layers
- **Database**: PostgreSQL (H2 for testing)
- **ORM**: jOOQ (DDL-based codegen from Flyway migrations) — NOT JPA
- **Concurrency**: Virtual Threads (Project Loom) — blocking JDBC + Spring MVC on virtual threads
- **Testing**: Kotest (test runner + assertions + property testing) + MockK (mocking)
  - Always use Kotest as the test framework — do NOT use JUnit 5
  - Use Kotest spec styles (`DescribeSpec`, `FunSpec`, `BehaviorSpec`, etc.) as test runner
  - Use Kotest assertions (`shouldBe`, `shouldBeRight`, `shouldBeLeft`, etc.)
  - Use `kotest-property` for property-based testing
  - Use `kotest-assertions-arrow` for Arrow-kt Either assertions
- **Linting**: ktlint + detekt (custom rules)
- **Coverage**: Kover

## Domain-Specific Patterns

<!-- Customize: Replace these examples with your actual domain patterns. -->

### State Machine (example: OrderStatus)

When modeling status with explicit state transitions, use a sealed interface where each state is a data class/object.
Each state exposes ONLY the transitions valid from that state as methods.
Invalid transitions are compile-time errors (the method doesn't exist), NOT runtime exceptions.

```
# Example state transition diagram:
Created → Confirmed | Canceled
Confirmed → Shipped | Canceled
Shipped → Delivered
Delivered → Returned (within 30 days only)
Returned → (terminal)
Canceled → (terminal)
```

### Money Value Object

When the domain includes monetary amounts, Money MUST carry both amount (BigDecimal) and currency (enum).
Arithmetic operations MUST validate currency match at the type level.
JPY MUST use scale=0; USD/EUR MUST use scale=2.

### Compensating Transactions

When an operation fails after partial side effects (e.g., payment fails after inventory reserved),
the use case MUST handle rollback via `mapLeft` with compensating actions.
Do NOT rely on `@Transactional` for cross-service consistency — use explicit Either-based compensation.

### Domain Service

When domain logic involves **multiple entities or aggregates** and does not naturally belong to a single entity,
implement it as a Domain Service in `domain/service/`.

Domain Services follow all domain layer rules:
- NO Spring annotations, NO framework dependencies
- NO `throw` — return `Either<XxxError, T>`
- NO `var`, NO mutable collections
- Only allowed imports: Kotlin stdlib, Arrow (`arrow.core.*`), kotlinx.coroutines, SLF4J

Domain Services are stateless. They receive entities/value objects as parameters and return results.
Use cases in the application layer orchestrate Domain Services along with repositories and ports.
Domain Services are wired via `@Bean` in `framework/config/`, same as use cases — no `@Component` or `@Service`.

### Clock Injection

All time-dependent logic (e.g., deadline checks, expiration windows) MUST use a `ClockPort` port interface.
Do NOT call `Instant.now()` or `LocalDate.now()` directly. This enables deterministic testing.

### CQRS: Command / Query Separation

Read and write paths are fully separated:

- **Command (write)**: Presentation → Command UseCase → Domain Entity → Domain Repository (write-only interface in `domain/`). Domain entities carry business logic, validation, and state transitions.
- **Query (read)**: Presentation → Query UseCase → Query Repository (interface in `application/query/repository/`) → Infrastructure (jOOQ + JDBC → DTO). No domain entity reconstruction. DTOs flow directly from DB to presentation.

Query repository interfaces and DTOs live in `application/query/`. Infrastructure implements them using jOOQ projections (blocking JDBC) directly into DTOs. This avoids unnecessary validation of already-persisted data and decouples the read model from domain entity structure.

All query repository methods return `Either<QueryError, T>` — consistent with the project-wide error handling convention.

### Virtual Threads

This project uses **Virtual Threads (Project Loom)** instead of reactive programming (R2DBC/WebFlux):

- **Database**: Blocking JDBC via jOOQ DSL. Virtual threads handle concurrency — no need for R2DBC or reactive streams.
- **Web layer**: Spring MVC (not WebFlux). Virtual threads handle concurrent requests efficiently.
- **Functions**: Repository interfaces, use case interfaces, port interfaces, and controller methods are **regular functions (not `suspend`)**. Blocking calls are safe on virtual threads.
- **Coroutines**: `kotlinx-coroutines-core` remains an allowed dependency in domain/application, but `suspend` is NOT required at interface boundaries.
- **Configuration**: `spring.threads.virtual.enabled=true` in `application.yaml`.

> For implementation code patterns, see `ca-kotlin` skill (`references/layer-rules.md`), `fp-kotlin` skill (`references/fp-patterns.md`), and `arrow-kt` skill.

## Coding Conventions

### Error handling

**Core rule**: Outside of presentation, `throw` is forbidden. Every fallible operation returns `Either<XxxError, T>`.

Layer-specific rules:
- `domain`: All functions that can fail return `Either<DomainError, T>`. No exceptions.
- `application`: All use case methods return `Either<ApplicationError, T>`. All query repository methods return `Either<QueryError, T>`. No exceptions.
- `infrastructure`: Wrap all DB/external calls with `Either.catch { }`. Use blocking JDBC on virtual threads.
- `presentation`: Call `.fold()` on `Either` results. MAY throw `ResponseStatusException` only.

### Immutability (FP principle)

- Prefer `val` over `var` everywhere. `var` is forbidden in `domain` and `application`.
- Domain entities must be `data class` with only `val` fields. Use `.copy()` for updates.
- Collections in domain/application must be immutable (`List`, `Map`, not `MutableList`).

### Value objects

- Wrap primitive IDs and constrained values in `@JvmInline value class`
- Use a private constructor + `companion object { operator fun invoke(...) }` pattern
- For validated value objects, provide a `fun of(...)` returning `Either`

### Naming

- Domain errors: `XxxError` (sealed interface in `domain/error/`)
- Domain services: `XxxDomainService` (in `domain/service/`, stateless, multi-entity logic)
- Domain repository interfaces: `XxxRepository` (in `domain/repository/`, write-only)
- Command use case interfaces: `XxxCreateUseCase`, `XxxUpdateUseCase` (in `application/command/usecase/`)
- Query use case interfaces: `XxxFindByIdQueryUseCase`, `XxxListQueryUseCase` (in `application/query/usecase/`)
- Command use case implementations: `XxxCreateUseCaseImpl`, `XxxUpdateUseCaseImpl` (in same package as interface)
- Query use case implementations: `XxxFindByIdQueryUseCaseImpl`, `XxxListQueryUseCaseImpl` (in same package as interface)
- Command application errors: `XxxCreateError`, `XxxUpdateError` (in `application/command/error/`)
- Query application errors: `XxxFindByIdQueryError`, `XxxListQueryError` (in `application/query/error/`)
- Query DTOs: `XxxDto`, `PageDto` (in `application/query/dto/`)
- Query repository interfaces: `XxxQueryRepository` (in `application/query/repository/`)
- Query repository implementations: `XxxQueryRepositoryImpl` (in `infrastructure/query/repository/`)
- Domain repository implementations: `XxxRepositoryImpl` (in `infrastructure/command/repository/`)
- Shared ports: `XxxPort` (e.g., `ClockPort`, `TransactionPort`) in `application/port/`
- Command ports: `XxxPort` (in `application/command/port/`)
- Query ports: `XxxQueryPort` (in `application/query/port/`, if needed)
- Command port adapter implementations: `XxxAdapter` (in `infrastructure/command/adapter/`)
- Query port adapter implementations: `XxxQueryAdapter` (in `infrastructure/query/adapter/`)
- Domain-to-infra mapping: `fun XxxRecord.toDomain()` in companion object

### DI

- DI wiring is done in `framework/config/`
- No `@Autowired` — constructor injection only
- `UseCaseConfig` wires both command use cases (with domain repository) and query use cases (with query repository)

## Language

- Code, comments, variable names: English
- Git commit messages: English

## Commands

- `./gradlew detekt` — static analysis (custom rules: ForbiddenLayerImport, NoThrowOutsidePresentation, NoExplicitAny)
- `./gradlew ktlintCheck` — lint check
- `./gradlew test` — run all tests
- `./gradlew koverVerify` — coverage check (80% minimum for domain, application, presentation)
- `./gradlew check` — run all checks

## Specifications

- Feature spec: see `.claude/specs/` directory
- Test cases: see `.claude/specs/test-cases.md`
