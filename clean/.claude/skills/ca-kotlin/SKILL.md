---
name: ca-kotlin
description: >
  Clean Architecture rules and patterns for Kotlin + Spring Boot + Arrow-kt projects.
  Use when implementing or reviewing any CA layer: domain modeling, use case design,
  repository patterns, infrastructure implementation, or controller wiring.
  Triggers on: domain entity design, value object creation, repository interface definition,
  use case implementation, jOOQ repository, REST controller, DI wiring.
---

# Clean Architecture — Kotlin

## Layer Dependency Direction

```
framework → presentation → application → domain ← infrastructure
```

## CQRS Pattern

The application layer is split into **command/** (writes) and **query/** (reads):

- **Command side**: `application/command/` → uses domain repository (in `domain/`) → returns domain entities
- **Query side**: `application/query/` → uses query repository (in `application/query/repository/`) → returns flat DTOs

## Key Patterns

| Concept | Pattern |
|---------|---------|
| Entity | `data class Entity(val id: EntityId, ...)` — `val` only, `.copy()` for updates |
| Value Object | `@JvmInline value class VO private constructor(val value: T)` with `of()` returning `Either` |
| State Machine | `sealed interface Status` with typed transitions as methods on each state |
| Domain Error | `sealed interface XxxError : DomainError` |
| Domain Service | `XxxDomainService` in `domain/service/` — stateless, multi-entity domain logic, returns `Either` |
| Domain Repo Interface | In `domain/`, write methods only (`save`, `delete`), returns `Either<XxxError, T>` |
| Command Use Case | `interface CreateXxxUseCase` in `application/command/` — uses domain repository |
| Query Repo Interface | In `application/query/repository/`, read methods, returns `Either<QueryError, QueryDto>` |
| Query Use Case | `interface XxxListQueryUseCase` in `application/query/` — uses query repository |
| Query DTO | `data class XxxQueryDto(val id: Long, ...)` — flat primitives, no domain types |
| Infra Domain Repo | `@Repository class XxxRepositoryImpl(dsl: DSLContext)` in `infrastructure/command/repository/` (blocking JDBC) |
| Infra Query Repo | `@Repository class XxxQueryRepositoryImpl(dsl: DSLContext)` in `infrastructure/query/repository/` (blocking JDBC) |
| Shared Port | `ClockPort`, `TransactionPort` in `application/port/` |
| Command Port | `PaymentPort` in `application/command/port/` |
| Port Adapter | `XxxAdapter` in `infrastructure/command/adapter/` or `infrastructure/query/adapter/` |
| REST Controller | `@RestController` injecting both command and query use cases |
| DI Wiring | `@Bean` for both command use cases (domain repo) and query use cases (query repo) |

## Forbidden Imports by Layer

| Layer | Forbidden |
|-------|-----------|
| `domain` | `org.springframework.*`, `jakarta.*`, `*.application.*`, `*.infrastructure.*`, jOOQ |
| `application` | Same as domain + no infra/presentation imports |
| `presentation` | `*.infrastructure.*` only (may import `*.domain.*` for DTO mapping) |

## Strict Rules

- NO `throw` in domain/application/infrastructure — use `Either`
- NO `@Component`/`@Service` on use case classes — wire manually via `@Bean`
- NO new library deps in `domain` or `application` `build.gradle.kts`
- NO `var` in domain or application — immutability enforced
- NO `Instant.now()` / `LocalDate.now()` — use `ClockPort`

**This project uses jOOQ + JDBC with virtual threads (NOT JPA, NOT R2DBC).** All interface methods (repos, use cases, ports, controllers) are regular functions (not `suspend`). Blocking JDBC calls are safe on virtual threads. See [references/layer-rules.md](references/layer-rules.md) for detailed layer implementation examples. For Arrow-kt API patterns (`either{}`, `.bind()`, `mapLeft`, `Either.catch`, `.fold()`): see the `arrow-kt` skill.
