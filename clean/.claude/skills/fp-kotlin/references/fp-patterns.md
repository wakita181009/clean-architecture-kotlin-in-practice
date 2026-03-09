# Functional Programming Patterns

## 1. Money Arithmetic with Currency Safety

```kotlin
// Example: Money with validated operations
data class Money private constructor(val amount: BigDecimal, val currency: Currency) {
    operator fun plus(other: Money): Either<MoneyError, Money> = either {
        ensure(currency == other.currency) { MoneyError.CurrencyMismatch(currency, other.currency) }
        Money(amount + other.amount, currency)
    }

    operator fun minus(other: Money): Either<MoneyError, Money> = either {
        ensure(currency == other.currency) { MoneyError.CurrencyMismatch(currency, other.currency) }
        Money(amount - other.amount, currency)
    }

    operator fun times(quantity: Int): Money =
        Money(amount * BigDecimal(quantity), currency)
}
```

## 2. Line Item Total Calculation (Pure Function)

```kotlin
// Example: summing line items with currency validation
fun calculateTotal(items: List<LineItem>): Either<MoneyError, Money> = either {
    ensure(items.isNotEmpty()) { MoneyError.EmptyItems }
    val currency = items.first().unitPrice.currency
    items.fold(Money(BigDecimal.ZERO, currency)) { acc, item ->
        (acc + item.lineTotal).bind()
    }
}
```

## 3. Compensating Transaction (Explicit Rollback)

```kotlin
// BAD (classic): try-catch-finally with mutable state
try {
    resourceService.reserve(items)
    externalService.execute(total)
} catch (e: ServiceException) {
    resourceService.release(items)  // might also throw!
    throw e
}

// GOOD (clean): Either chain with mapLeft compensation
either {
    val reserved = reserveAll(items).bind()

    externalPort.call(total, method)
        .mapLeft { error ->
            // Compensate: release everything we reserved
            reserved.forEach { (id, qty) ->
                resourceRepo.release(id, qty)
            }
            CreateXxxError.ExternalServiceFailed(error)
        }.bind()
}
```

## 4. State Transition with Typed Methods

```kotlin
// Example: each state ONLY exposes valid transitions
sealed interface Status {
    data object Created : Status {
        fun confirm(ref: Reference): Confirmed
        fun cancel(reason: CancelReason, at: Instant): Canceled
        // NO ship(), NO deliver()
    }
    data class Delivered(val deliveredAt: Instant) : Status {
        fun processReturn(reason: ReturnReason, at: Instant): Returned
        // NO cancel(), NO ship()
    }
}

// Usage in use case:
val entity = repo.findById(id).bind()
when (val status = entity.status) {
    is Status.Delivered -> {
        val returned = status.processReturn(reason, clock.now())
        entity.copy(status = returned)
    }
    else -> raise(XxxError.InvalidStatusForReturn(status::class.simpleName ?: "unknown"))
}
```

## 5. Clock Injection for Deterministic Testing

```kotlin
// BAD: untestable
if (Instant.now().isAfter(eventTime.plus(30, ChronoUnit.DAYS))) {
    throw WindowExpiredException()
}

// GOOD: testable, deterministic
val now = clockPort.now()
val deadline = eventTime.plus(30, ChronoUnit.DAYS)
if (now.isAfter(deadline)) {
    raise(XxxError.WindowExpired(eventTime, deadline))
}

// In test (regular function, not suspend — use every, not coEvery):
every { clockPort.now() } returns Instant.parse("2024-02-01T00:00:00Z")
```

## 6. Extension Functions for Layer Mapping

```kotlin
// Domain → Presentation (in presentation layer)
fun Entity.toResponse() = EntityResponse(
    id = id.value,
    status = status::class.simpleName ?: "unknown",
    totalAmount = MoneyResponse(totalAmount.amount, totalAmount.currency.name),
)

// Request → Application DTO (in presentation layer)
fun CreateXxxRequest.toInput() = CreateXxxInput(
    name = name,
    items = items.map { ItemInput(it.productId, it.quantity) },
    // ...
)

// DB Record → Domain (in infrastructure layer)
fun XxxRecord.toDomain(children: List<Child>) = Entity(
    id = XxxId(id!!),
    name = Name(name!!),
    // ...
)
```
