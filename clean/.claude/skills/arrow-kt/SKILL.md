---
name: arrow-kt
description: >
  Arrow-kt patterns for this Clean Architecture Kotlin project. Use when writing or reviewing
  code that involves Either, either{} DSL, bind(), mapLeft(), Either.catch(), ensure(), or fold().
---

# Arrow-kt Patterns

## Imports

```kotlin
import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
```

## Core APIs

### `either { }` + `.bind()` — compose multiple Either operations

**MANDATORY**: When a function returns `Either` and orchestrates multiple Either operations,
always use `either { }` DSL with `.bind()`. Never use imperative `return .left()` / `.fold({ return it.left() }) { it }`.

```kotlin
override fun execute(input: CreateOrderInput): Either<CreateOrderError, Order> =
    either {
        val customerId = CustomerId.of(input.customerId)
            .mapLeft(CreateOrderError::InvalidCustomerId)
            .bind()   // short-circuits on Left automatically

        val items = validateItems(input.items).bind()

        val reserved = reserveInventory(items).bind()

        val payment = paymentPort.charge(total, method)
            .mapLeft { err ->
                releaseInventory(reserved)  // compensate
                CreateOrderError.PaymentFailed(err)
            }.bind()

        orderRepo.save(confirmedOrder)
            .mapLeft(CreateOrderError::RepositoryError)
            .bind()
    }
```

### `ensureNotNull()` — null check that short-circuits with a typed error

Use inside `either { }` to convert a nullable value into a non-null value, raising a Left if null.

```kotlin
either {
    val maybePlan = planRepository.findById(validPlanId)
        .mapLeft { SubscriptionCreateError.InternalError(it) }
        .bind()   // Either<E, Plan?> → Plan?

    val plan = ensureNotNull(maybePlan) {
        SubscriptionCreateError.PlanNotFound(validPlanId)
    }   // Plan? → Plan (smart-cast to non-null)

    ensure(plan.active) {
        SubscriptionCreateError.PlanNotActive(validPlanId)
    }
}
```

### `.mapLeft()` — convert error types at layer boundaries

```kotlin
// Method reference (when wrapper takes upstream error as sole arg)
.mapLeft(CreateOrderError::PaymentFailed)

// Lambda (when transformation is conditional)
.mapLeft { error ->
    when (error) {
        is OrderError.NotFound -> ReturnOrderError.NotFound(error)
        else -> ReturnOrderError.RepositoryError(error)
    }
}
```

### `Either.catch { }` — wrap infrastructure exceptions

```kotlin
Either.catch {
    dsl.selectFrom(ORDERS)
        .where(ORDERS.ID.eq(id.value))
        .fetchOne()
}.mapLeft { OrderError.RepositoryError("findById failed: ${it.message}", it) }
```

### `ensure()` — conditional validation

```kotlin
fun of(value: Int): Either<QuantityError, Quantity> = either {
    ensure(value >= MIN_VALUE) { QuantityError.BelowMinimum(value) }
    ensure(value <= MAX_VALUE) { QuantityError.AboveMaximum(value) }
    Quantity(value)
}
```

### `.fold()` — consume Either in presentation

```kotlin
useCase.execute(input).fold(
    ifLeft = { error ->
        when (error) {
            is CreateOrderError.InvalidCustomerId ->
                ResponseEntity.badRequest().body(ErrorResponse(error.message))
            is CreateOrderError.PaymentFailed ->
                ResponseEntity.status(502).body(ErrorResponse(error.message))
            // ... exhaustive when
        }
    },
    ifRight = { order ->
        ResponseEntity.status(201).body(OrderResponse.fromDomain(order))
    },
)
```

## Anti-Patterns

### Imperative `return .left()` / `.fold({ return }) { it }` (FORBIDDEN)

```kotlin
// BAD: imperative early-return style — verbose, hard to read, loses FP composability
fun execute(customerId: Int, planId: Int): Either<SubscriptionCreateError, Subscription> {
    val validCustomerId = CustomerId.of(customerId)
        .mapLeft { SubscriptionCreateError.InvalidInput(it.toString()) }
        .fold({ return it.left() }) { it }       // ← NEVER do this

    val plan = planRepository.findById(validPlanId)
        .mapLeft { SubscriptionCreateError.InternalError(it) }
        .fold({ return it.left() }) { it }        // ← NEVER do this
        ?: return SubscriptionCreateError.PlanNotFound(validPlanId).left()  // ← NEVER do this

    if (!plan.active) {
        return SubscriptionCreateError.PlanNotActive(validPlanId).left()    // ← NEVER do this
    }
    // ...
}

// GOOD: either {} DSL — declarative, concise, all short-circuiting handled by bind()/ensure()
fun execute(customerId: Int, planId: Int): Either<SubscriptionCreateError, Subscription> =
    either {
        val validCustomerId = CustomerId.of(customerId)
            .mapLeft { SubscriptionCreateError.InvalidInput(it.toString()) }
            .bind()

        val plan = ensureNotNull(
            planRepository.findById(validPlanId)
                .mapLeft { SubscriptionCreateError.InternalError(it) }
                .bind()
        ) { SubscriptionCreateError.PlanNotFound(validPlanId) }

        ensure(plan.active) { SubscriptionCreateError.PlanNotActive(validPlanId) }

        // ...
    }
```

### Other anti-patterns

```kotlin
// BAD: throw in domain/application/infrastructure
if (value <= 0) throw IllegalArgumentException("must be positive")

// GOOD: Either
ensure(value > 0) { OrderError.InvalidId(value) }

// BAD: bare nullable return
fun findById(id: Long): Order?

// GOOD: typed error
fun findById(id: OrderId): Either<OrderError, Order>

// BAD: swallowing errors with getOrNull
val order = useCase.execute(id).getOrNull() ?: return notFound()

// GOOD: exhaustive fold
useCase.execute(id).fold(ifLeft = { ... }, ifRight = { ... })
```

## Complete Refactoring Example (Before → After)

### Before (imperative style — DO NOT write new code like this)

```kotlin
override fun execute(customerId: Int, planId: Int): Either<SubscriptionCreateError, Subscription> {
    val validCustomerId = CustomerId.of(customerId)
        .mapLeft { SubscriptionCreateError.InvalidInput(it.toString()) }
        .fold({ return it.left() }) { it }

    val validPlanId = PlanId.of(planId)
        .mapLeft { SubscriptionCreateError.InvalidInput(it.toString()) }
        .fold({ return it.left() }) { it }

    val plan = planRepository.findById(validPlanId)
        .mapLeft { SubscriptionCreateError.InternalError(it) }
        .fold({ return it.left() }) { it }
        ?: return SubscriptionCreateError.PlanNotFound(validPlanId).left()

    if (!plan.active) {
        return SubscriptionCreateError.PlanNotFound(validPlanId).left()
    }

    val existingSub = subscriptionRepository.findActiveByCustomerId(validCustomerId)
        .mapLeft { SubscriptionCreateError.InternalError(it) }
        .fold({ return it.left() }) { it }

    if (existingSub != null) {
        return SubscriptionCreateError.AlreadySubscribed(validCustomerId).left()
    }

    val subscription = Subscription.create(
        id = SubscriptionId(0), customerId = validCustomerId,
        plan = plan, now = clockPort.now(),
    )
    return subscriptionRepository.save(subscription)
        .mapLeft { SubscriptionCreateError.InternalError(it) }
}
```

### After (either {} DSL — THIS is the correct style)

```kotlin
override fun execute(customerId: Int, planId: Int): Either<SubscriptionCreateError, Subscription> =
    either {
        val validCustomerId = CustomerId.of(customerId)
            .mapLeft { SubscriptionCreateError.InvalidInput(it.toString()) }
            .bind()

        val validPlanId = PlanId.of(planId)
            .mapLeft { SubscriptionCreateError.InvalidInput(it.toString()) }
            .bind()

        val plan = ensureNotNull(
            planRepository.findById(validPlanId)
                .mapLeft { SubscriptionCreateError.InternalError(it) }
                .bind()
        ) { SubscriptionCreateError.PlanNotFound(validPlanId) }

        ensure(plan.active) { SubscriptionCreateError.PlanNotFound(validPlanId) }

        val existingSub = subscriptionRepository.findActiveByCustomerId(validCustomerId)
            .mapLeft { SubscriptionCreateError.InternalError(it) }
            .bind()

        ensure(existingSub == null) { SubscriptionCreateError.AlreadySubscribed(validCustomerId) }

        val subscription = Subscription.create(
            id = SubscriptionId(0), customerId = validCustomerId,
            plan = plan, now = clockPort.now(),
        )

        subscriptionRepository.save(subscription)
            .mapLeft { SubscriptionCreateError.InternalError(it) }
            .bind()
    }
```
