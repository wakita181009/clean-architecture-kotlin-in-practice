# Classic Subscription Service

## Project Overview

A traditional single-module Spring Boot subscription management API.
Conventional layered architecture with standard Spring Boot annotations and patterns.

## Tech Stack

- **Language**: Kotlin 2.3.10
- **Framework**: Spring Boot 4.0.3 (Spring MVC, blocking)
- **ORM**: Spring Data JPA (Hibernate) — NOT jOOQ
- **Database**: PostgreSQL (H2 for testing)
- **Validation**: Jakarta Bean Validation (`spring-boot-starter-validation`)
- **Testing**: JUnit 5 (test runner) + MockK (mocking) + Spring Boot Test
  - Always use JUnit 5 as the test framework — do NOT use Kotest
  - Backtick method names for readability: `` `should create subscription`() ``
- **Linting**: ktlint
- **Build**: Gradle (Kotlin DSL), single module

## Package Structure

```
com.wakita181009.classic/
├── model/          # JPA entities, enums, value objects (Money)
├── repository/     # Spring Data JPA repository interfaces
├── service/        # @Service business logic
├── controller/     # @RestController REST endpoints
├── dto/            # Request/Response DTOs
├── exception/      # Custom exceptions + @ControllerAdvice handler
└── config/         # Spring @Configuration classes
```

Base package: `com.wakita181009.classic`

## Conventions

Detailed code patterns are in `.claude/rules/` and auto-applied per file type:
- `rules/jpa-kotlin.md` — Entity, enum, VO, repository patterns (applied to `model/`, `repository/`)
- `rules/spring-kotlin.md` — Service, controller, DTO, exception patterns (applied to `service/`, `controller/`, `dto/`, `exception/`, `config/`)
- `rules/testing.md` — Test patterns for all layers (applied to `src/test/`)

### Key Rules (always apply)

- Constructor injection only — no `@Autowired`
- `class` (not `data class`) for JPA entities
- `@Enumerated(EnumType.STRING)` always — never ORDINAL
- `@Transactional` for writes, `@Transactional(readOnly = true)` for reads
- Inject `java.time.Clock` for time — never `Instant.now()` directly in services

### Testing

| Layer | Approach | Spring Context |
|-------|----------|----------------|
| Model (entities, enums, VOs) | Plain JUnit5, no mocks | No |
| Service | `@ExtendWith(MockKExtension::class)`, mock repositories | No |
| Repository | `@DataJpaTest` + H2 | Partial |
| Controller | `@WebMvcTest` + `@MockkBean` | Partial |
| Integration | `@SpringBootTest` + `@AutoConfigureMockMvc` | Full |

## Naming Conventions

| Concept | Pattern | Example |
|---------|---------|---------|
| Entity | `Xxx` | `Subscription`, `Plan`, `Invoice` |
| Enum | `XxxStatus`, `XxxType` | `SubscriptionStatus`, `PlanTier` |
| Value Object | `Xxx` | `Money`, `BillingInterval` |
| Repository | `XxxRepository` | `SubscriptionRepository` |
| Service | `XxxService` | `SubscriptionService` |
| Controller | `XxxController` | `SubscriptionController` |
| Request DTO | `XxxRequest` | `CreateSubscriptionRequest` |
| Response DTO | `XxxResponse` | `SubscriptionResponse` |
| Exception | `XxxException` | `SubscriptionNotFoundException` |
| Test | `XxxTest` | `SubscriptionServiceTest` |

## Language

- Code, comments, variable names: English
- Git commit messages: English

## Commands

- `./gradlew ktlintCheck` — lint check
- `./gradlew ktlintFormat` — auto-format
- `./gradlew test` — run all tests
- `./gradlew check` — run all checks (lint + test)

## Specifications

- Phase 0 feature spec: `.claude/specs/subscription-api-0.md`
- Phase 0 test cases: `.claude/specs/subscription-test-cases-0.md`
- Phase 1 feature spec: `.claude/specs/subscription-api-1.md`
- Phase 1 test cases: `.claude/specs/subscription-test-cases-1.md`
