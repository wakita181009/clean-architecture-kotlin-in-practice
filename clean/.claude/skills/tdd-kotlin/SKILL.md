---
name: tdd-workflow
description: >
  TDD workflow for Clean Architecture Kotlin projects. Use when implementing new features,
  fixing bugs, or refactoring code. Enforces red-green-refactor cycle with 80%+ coverage
  using Kotest + MockK + Arrow-kt.
---

# TDD Workflow for Clean Architecture Kotlin

This skill enforces test-driven development across all CA layers.

## When to Activate

- Writing new features (domain entities, use cases, controllers)
- Fixing bugs
- Refactoring existing code
- Adding API endpoints

## TDD Cycle: Red → Green → Refactor

### Step 1: Identify the Layer and Write Tests First (RED)

Determine which CA layer the change belongs to, then write failing tests.

| Layer | Test Style | What to Test |
|-------|-----------|--------------|
| Domain | Pure Kotest, no mocks, property-based | Value objects, entities, state machines, domain services |
| Application | MockK for repositories/ports | Command use cases, query use cases, error mapping |
| Presentation | MockK for use cases | HTTP status codes, DTO mapping, error responses |
| Infrastructure | Integration tests (H2/testcontainers) | jOOQ queries, port adapter implementations |

### Step 2: Write Minimal Tests

```kotlin
// Example: Domain value object
class QuantityTest : DescribeSpec({
    describe("of") {
        it("returns Right for valid values") {
            Quantity.of(5).shouldBeRight().value shouldBe 5
        }
        it("returns Left for zero") {
            Quantity.of(0).shouldBeLeft()
        }
    }
})
```

### Step 3: Run Tests — They MUST Fail

```bash
cd clean && ./gradlew test
```

Verify the tests fail for the right reason (missing class/method, not a test framework error).

### Step 4: Implement Minimal Code (GREEN)

Write just enough code to make the tests pass. Follow these layer rules:
- Domain/Application: No Spring, no `throw`, return `Either<Error, T>`
- Use `either {}` DSL with `.bind()`, not imperative `return .left()`
- Use `val` only (no `var`)

### Step 5: Run Tests Again — They MUST Pass

```bash
cd clean && ./gradlew test
```

### Step 6: Refactor

Improve code while keeping tests green:
- Extract value objects
- Improve naming
- Eliminate duplication
- Ensure FP patterns are applied correctly

### Step 7: Verify Coverage

```bash
cd clean && ./gradlew koverVerify
# 80% minimum for domain, application, presentation
```

## Implementation Order (Inside-Out)

When implementing a new feature across layers, follow this order:

1. **Domain** — Value objects, entities, domain errors, domain services
2. **Application** — Use case interfaces, DTOs, application errors, use case implementations
3. **Infrastructure** — Repository implementations, port adapters
4. **Presentation** — Controllers, request/response DTOs
5. **Framework** — DI wiring in `UseCaseConfig`

Each layer: write tests first → implement → refactor → move to next layer.

## Test Patterns by Layer

### Domain Tests

- **No mocks** — domain is pure logic
- Use `checkAll(Arb.xxx())` for property-based testing of value objects
- Use `shouldBeRight()` / `shouldBeLeft()` for Either results
- State machine: test valid transitions, document invalid ones as compile-time errors

### Application Tests

- Mock all dependencies: `mockk<XxxRepository>()`, `mockk<ClockPort>()`
- `beforeTest { clearMocks(...) }` to reset between tests
- Test happy path → returns `Right`
- Test each error path → returns specific `Left` error type
- Verify side effects: `verify(exactly = 1) { repo.save(any()) }`

### Presentation Tests

- Mock use cases: `mockk<CreateXxxUseCase>()`
- Test HTTP status mapping: `Right` → 200/201, `Left` → 400/404/409
- Direct controller instantiation (no Spring context needed)

### Infrastructure Tests

- Integration tests with real DB (H2 or testcontainers)
- Verify jOOQ queries return correct data
- Verify mapping from DB records to domain entities

## Forbidden in Tests

- `coEvery` / `coVerify` (methods are not `suspend` — virtual threads)
- `runTest { }` / `runBlocking { }` (not needed)
- JUnit 5 `@Test` annotation (use Kotest spec styles)
- Testing implementation details (test behavior, not internals)

## Quality Checklist

Before moving to the next layer or finishing a feature:

- [ ] All tests pass (`./gradlew test`)
- [ ] No skipped or disabled tests
- [ ] Coverage meets 80% threshold (`./gradlew koverVerify`)
- [ ] Static analysis passes (`./gradlew detekt`)
- [ ] Lint passes (`./gradlew ktlintCheck`)
- [ ] Each test is independent (no shared mutable state)
- [ ] Error paths are tested, not just happy paths
- [ ] Property-based tests cover boundaries for value objects

See `tdd-kotlin` skill for detailed test code patterns per layer.
