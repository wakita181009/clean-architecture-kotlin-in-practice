---
description: Applies when writing or reviewing Kotlin test files
globs:
  - "**/*Test.kt"
  - "**/*Spec.kt"
  - "**/*Tests.kt"
---

# Test Conventions (Virtual Threads Project)

This project uses virtual threads — all methods are regular functions (not `suspend`).

## FORBIDDEN in test files

- `coEvery` / `coVerify` — use `every` / `verify` instead (methods are not `suspend`)
- `runTest { }` — not needed (no coroutines at interface boundaries)
- `runBlocking { }` — not needed (virtual threads handle concurrency)
- JUnit 5 `@Test` annotation — use Kotest spec styles (`DescribeSpec`, `FunSpec`, `BehaviorSpec`)

## Required

- Use **Kotest** spec styles as the test runner
- Use `every { }` / `verify { }` for MockK stubbing and verification
- Use `clearMocks(...)` in `beforeTest` to reset mocks between tests
