---
name: fp-kotlin
description: >
  Functional programming patterns for Kotlin with Arrow-kt. Use when writing immutable domain
  models, designing pure functions, working with Either error handling, creating value objects,
  or applying sealed interface error hierarchies.
---

# FP in Kotlin with Arrow-kt

## Core Principles

1. **Immutability** — `val` over `var`; entities use `.copy()` for updates; collections are `List`/`Map`
2. **Explicit Errors** — `Either<Error, Value>` instead of exceptions (except presentation layer)
3. **Type-Driven Design** — make illegal states unrepresentable via value objects and sealed state machines
4. **Total Functions** — handle all inputs; no throwing in domain/application/infrastructure

## Either Quick Reference

| Pattern | Usage |
|---------|-------|
| `either { }` + `.bind()` | Chain operations, short-circuit on first Left |
| `ensure(cond) { err }` | Conditional raise inside `either {}` |
| `raise(error)` | Immediate Left inside `either {}` |
| `Either.catch { }` | Wrap exceptions from infrastructure |
| `.mapLeft { }` | Transform error type at layer boundary |
| `.fold(ifLeft, ifRight)` | Consume in presentation layer |

## State Machine Pattern

```kotlin
sealed interface OrderStatus {
    data object Created : OrderStatus {
        fun confirm(paymentId: PaymentId): Confirmed = Confirmed(paymentId)
        fun cancel(reason: CancelReason, at: Instant): Canceled = Canceled(reason, at)
    }
    data class Confirmed(val paymentId: PaymentId) : OrderStatus {
        fun ship(tracking: TrackingNumber, at: Instant): Shipped = Shipped(tracking, at)
    }
    // Invalid: Created.ship() → compile error (method doesn't exist)
}
```

## Compensating Transaction Pattern

```kotlin
either {
    val reserved = reserveInventory(items).bind()
    paymentPort.charge(total, method)
        .mapLeft { error ->
            releaseInventory(reserved)  // compensate on failure
            CreateOrderError.PaymentFailed(error)
        }.bind()
}
```

See [references/fp-patterns.md](references/fp-patterns.md) for complete patterns.
