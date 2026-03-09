# Clean Architecture Layer Rules

## Layer 1: Domain (Innermost)

**Allowed dependencies**: Kotlin stdlib, Arrow-kt, kotlinx.coroutines, SLF4J

### Value Objects
```kotlin
// Example: ID value object
@JvmInline
value class XxxId private constructor(val value: Long) {
    companion object {
        operator fun invoke(value: Long) = XxxId(value)
        fun of(value: Long): Either<XxxError, XxxId> = either {
            ensure(value > 0L) { XxxError.InvalidId(value) }
            XxxId(value)
        }
    }
}
```

### Money (multi-field value object)
```kotlin
// Example: Money with currency safety
data class Money private constructor(val amount: BigDecimal, val currency: Currency) {
    enum class Currency(val scale: Int) { USD(2), EUR(2), JPY(0) }

    operator fun plus(other: Money): Either<MoneyError, Money> = either {
        ensure(currency == other.currency) { MoneyError.CurrencyMismatch(currency, other.currency) }
        Money(amount + other.amount, currency)
    }

    operator fun times(quantity: Int): Money =
        Money(amount * BigDecimal(quantity), currency)

    companion object {
        fun of(amount: BigDecimal, currency: Currency): Either<MoneyError, Money> = either {
            ensure(amount >= BigDecimal.ZERO) { MoneyError.NegativeAmount(amount) }
            ensure(amount.scale() <= currency.scale) { MoneyError.InvalidScale(amount, currency) }
            Money(amount.setScale(currency.scale, RoundingMode.HALF_UP), currency)
        }
        operator fun invoke(amount: BigDecimal, currency: Currency) = Money(amount, currency)
    }
}
```

### State Machine
```kotlin
// Example: OrderStatus with typed transitions
sealed interface OrderStatus {
    data object Created : OrderStatus {
        fun confirm(paymentId: PaymentId): Confirmed = Confirmed(paymentId)
        fun cancel(reason: CancelReason, canceledAt: Instant): Canceled = Canceled(reason, canceledAt)
    }
    data class Confirmed(val paymentId: PaymentId) : OrderStatus {
        fun ship(trackingNumber: TrackingNumber, shippedAt: Instant): Shipped =
            Shipped(trackingNumber, shippedAt)
        fun cancel(reason: CancelReason, canceledAt: Instant): Canceled = Canceled(reason, canceledAt)
    }
    // ... Shipped, Delivered, Returned, Canceled
}
```

Each state exposes ONLY valid transitions. No `ship()` on `Created`. No `cancel()` on `Shipped`.

### Domain Errors
```kotlin
interface DomainError { val message: String }

// Example: sealed interface per aggregate
sealed interface XxxError : DomainError {
    data class InvalidId(val value: Long) : XxxError {
        override val message = "Invalid ID: $value (must be positive)"
    }
    data class NotFound(val id: XxxId) : XxxError {
        override val message = "Not found: ${id.value}"
    }
    data class RepositoryError(override val message: String, val cause: Throwable? = null) : XxxError
}
```

### Domain Services

Domain Services encapsulate domain logic that spans **multiple entities or aggregates** and does not naturally belong to a single entity. They are stateless classes in `domain/service/`.

```kotlin
// Example: domain/service/PricingDomainService.kt
class PricingDomainService {
    fun calculateTotal(items: List<LineItem>, discount: Discount): Either<MoneyError, Money> =
        either {
            val subtotal = items.fold(Money.zero(items.first().unitPrice.currency)) { acc, item ->
                acc.plus(item.lineTotal).bind()
            }
            discount.applyTo(subtotal).bind()
        }
}
```

Rules:
- Same constraints as all domain code: no Spring, no `throw`, no `var`, no mutable collections
- Stateless — receive entities/value objects as parameters, return results
- Named `XxxDomainService` to distinguish from application-layer use cases
- Wired via `@Bean` in `framework/config/` (no `@Component` / `@Service`)
- Use cases in the application layer call domain services as needed

### Repository Interfaces (Command Only)
```kotlin
// Interface defined in domain, implemented in infrastructure
// CQRS: Only write operations — read operations are in application/query/repository/
// Regular functions (not suspend) — virtual threads handle concurrency
interface XxxRepository {
    fun save(entity: Xxx): Either<XxxError, Xxx>
    fun delete(id: XxxId): Either<XxxError, Unit>
}
```

---

## Layer 2: Application (CQRS)

**Purpose**: Orchestrates domain objects to fulfill use cases. Split into **command** (writes) and **query** (reads).

**Allowed dependencies**: domain layer, Arrow-kt, Kotlin stdlib/coroutines

**Package structure**:
```
application/
├── command/            # Write operations (go through domain layer)
│   ├── dto/            # Input DTOs that map to domain entities
│   ├── error/          # Command-specific application errors
│   ├── port/           # Command-side port interfaces
│   └── usecase/        # Command use cases
├── query/              # Read operations (bypass domain layer)
│   ├── dto/            # Query DTOs (flat primitives — no domain types)
│   ├── error/          # Query-specific application errors
│   ├── repository/     # Query repository interfaces (defined HERE, not in domain)
│   └── usecase/        # Query use cases
├── error/              # Shared base (ApplicationError)
└── port/               # Shared port interfaces (ClockPort, TransactionPort)
```

### Command Side (writes through domain)

#### Command Use Case with Compensating Transaction
```kotlin
// Example: use case with explicit rollback on failure
// Regular function (not suspend) — virtual threads handle concurrency
class CreateXxxUseCaseImpl(
    private val repo: XxxRepository,  // domain repo (write-only)
    private val resourceRepo: ResourceRepository,
    private val externalPort: ExternalServicePort,
    private val clockPort: ClockPort,
) : CreateXxxUseCase {
    override fun execute(input: CreateXxxInput): Either<CreateXxxError, Xxx> =
        either {
            // validate → reserve resources → call external service → save
            // ON external service failure: release resources in mapLeft
            externalPort.call(total, method)
                .mapLeft { error ->
                    releaseAllResources(reserved)  // compensating action
                    CreateXxxError.ExternalServiceFailed(error)
                }.bind()
        }
}
```

### Query Side (bypasses domain)

#### Query Repository Interface (in `application/query/repository/`)
```kotlin
// Regular functions (not suspend) — virtual threads handle concurrency
interface XxxQueryRepository {
    fun findById(id: Long): Either<XxxFindByIdQueryError, XxxQueryDto>
    fun list(limit: Int, offset: Int): Either<XxxListQueryError, PageDto<XxxQueryDto>>
}
```

#### Query DTO (flat primitives — no domain types)
```kotlin
data class XxxQueryDto(
    val id: Long,
    val name: String,
    val status: String,
    val createdAt: OffsetDateTime,
)

data class PageDto<T>(val items: List<T>, val totalCount: Long)
```

#### Query Use Case
```kotlin
class XxxFindByIdQueryUseCaseImpl(
    private val queryRepository: XxxQueryRepository,  // query repo (read-only)
) : XxxFindByIdQueryUseCase {
    override fun execute(id: Long): Either<XxxFindByIdQueryError, XxxQueryDto> =
        queryRepository.findById(id)
}
```

### Clock-based Logic
```kotlin
// NEVER: Instant.now()
// ALWAYS: clockPort.now()
val now = clockPort.now()
val deadline = eventTime.plus(30, ChronoUnit.DAYS)
if (now.isAfter(deadline)) raise(XxxError.WindowExpired(...))
```

---

## Layer 3: Infrastructure (CQRS)

**Purpose**: Implements domain repository interfaces (command), application query repository interfaces (query), and port adapters.

**Uses jOOQ + JDBC with virtual threads (NOT JPA, NOT R2DBC)**

**Package structure**:
```
infrastructure/
├── command/
│   ├── adapter/        # Command-side port implementations
│   └── repository/     # Command repo implementations (implements domain/ interfaces)
└── query/
    ├── adapter/        # Query-side port implementations (if needed)
    └── repository/     # Query repo implementations (implements application/query/repository/ interfaces)
```

### Command Repository (`infrastructure/command/repository/`)
```kotlin
@Repository
class XxxRepositoryImpl(private val dsl: DSLContext) : XxxRepository {
    // CQRS: write methods only
    // Blocking JDBC — safe on virtual threads
    override fun save(entity: Xxx): Either<XxxError, Xxx> =
        Either.catch {
            dsl.insertInto(XXX_TABLE)
                .set(XXX_TABLE.ID, entity.id.value)
                // ...
                .returning()
                .fetchOne()!!
                .toDomain()
        }.mapLeft { XxxError.RepositoryError("save failed: ${it.message}", it) }
}
```

### Query Repository (`infrastructure/query/repository/`)
```kotlin
@Repository
class XxxQueryRepositoryImpl(private val dsl: DSLContext) : XxxQueryRepository {
    override fun findById(id: Long): Either<XxxFindByIdQueryError, XxxQueryDto> =
        either {
            val record = Either.catch {
                dsl.selectFrom(XXX_TABLE)
                    .where(XXX_TABLE.ID.eq(id))
                    .fetchOne()
            }.mapLeft { XxxFindByIdQueryError.FetchFailed("findById failed: ${it.message}") }
                .bind()

            if (record == null) raise(XxxFindByIdQueryError.NotFound(id))
            record.toQueryDto()  // maps to flat DTO, NOT domain entity
        }
}
```

---

## Layer 4: Presentation

```kotlin
@RestController
@RequestMapping("/api/xxx")
class XxxController(
    // Command use cases
    private val createUseCase: CreateXxxUseCase,
    // Query use cases
    private val findByIdQueryUseCase: XxxFindByIdQueryUseCase,
    private val listQueryUseCase: XxxListQueryUseCase,
) {
    @PostMapping
    fun create(@RequestBody req: CreateXxxRequest): ResponseEntity<*> =
        createUseCase.execute(req.toInput()).fold(
            ifLeft = { error ->
                when (error) {
                    is CreateXxxError.InvalidInput -> ResponseEntity.badRequest().body(ErrorResponse(error.message))
                    is CreateXxxError.RepositoryError -> ResponseEntity.internalServerError().body(ErrorResponse("Internal error"))
                    // ... exhaustive when
                }
            },
            ifRight = { entity -> ResponseEntity.status(201).body(XxxResponse.fromDomain(entity)) },
        )

    @GetMapping("/{id}")
    fun findById(@PathVariable id: Long): ResponseEntity<*> =
        findByIdQueryUseCase.execute(id).fold(
            ifLeft = { error ->
                when (error) {
                    is XxxFindByIdQueryError.NotFound -> ResponseEntity.notFound().build<Nothing>()
                    is XxxFindByIdQueryError.FetchFailed -> ResponseEntity.internalServerError().body(ErrorResponse("Internal error"))
                }
            },
            ifRight = { dto -> ResponseEntity.ok(dto) },
        )
}
```

---

## Layer 5: Framework (CQRS wiring)

```kotlin
@Configuration
class UseCaseConfig(
    private val xxxRepo: XxxRepository,            // domain repo (write-only)
    private val xxxQueryRepo: XxxQueryRepository,  // query repo (read-only)
    private val clockPort: ClockPort,
    // ... other dependencies
) {
    // Command use cases (wired with domain repository)
    @Bean fun createXxxUseCase() = CreateXxxUseCaseImpl(xxxRepo, clockPort)

    // Query use cases (wired with query repository)
    @Bean fun xxxFindByIdQueryUseCase() = XxxFindByIdQueryUseCaseImpl(xxxQueryRepo)
    @Bean fun xxxListQueryUseCase() = XxxListQueryUseCaseImpl(xxxQueryRepo)
}
```
