---
paths:
  - "src/main/**/model/**/*.kt"
  - "src/main/**/repository/**/*.kt"
---

# JPA/Hibernate Patterns in Kotlin

## Entity Design

Use `class` (not `data class`) for JPA entities — Hibernate proxies need non-final classes.
The `kotlin-jpa` plugin generates no-arg constructors — do NOT add them manually.

```kotlin
@Entity
@Table(name = "subscriptions", indexes = [
    Index(name = "idx_subscriptions_customer_id", columnList = "customer_id"),
    Index(name = "idx_subscriptions_status", columnList = "status"),
])
class Subscription(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "customer_id", nullable = false)
    val customerId: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: SubscriptionStatus = SubscriptionStatus.TRIAL,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    val plan: Plan,

    @Column(nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(nullable = false)
    var updatedAt: Instant = Instant.now(),
)
```

Rules:
- `val` for immutable fields, `var` for mutable fields (status, updatedAt)
- `@Enumerated(EnumType.STRING)` always — never ORDINAL
- `@Table(indexes = [...])` for frequently queried columns
- Default `FetchType.LAZY` for all `@ManyToOne` relationships

## Enums

Use `enum class` for status values. Include `canTransitionTo()` for lifecycle enums.

```kotlin
enum class SubscriptionStatus {
    TRIAL, ACTIVE, PAUSED, PAST_DUE, CANCELED, EXPIRED;

    fun canTransitionTo(target: SubscriptionStatus): Boolean = when (this) {
        TRIAL -> target in setOf(ACTIVE, CANCELED)
        ACTIVE -> target in setOf(PAUSED, PAST_DUE, CANCELED, EXPIRED)
        PAUSED -> target in setOf(ACTIVE, CANCELED)
        PAST_DUE -> target in setOf(ACTIVE, CANCELED)
        CANCELED -> false
        EXPIRED -> false
    }
}
```

## Value Objects

Use `@Embeddable data class` for multi-field value objects. Validation in `init` block with `require()`.

```kotlin
@Embeddable
data class Money(
    @Column(nullable = false)
    val amount: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val currency: Currency,
) {
    enum class Currency(val scale: Int) {
        USD(2), EUR(2), JPY(0)
    }

    init {
        require(amount.scale() <= currency.scale) {
            "Scale ${amount.scale()} exceeds ${currency.name} scale ${currency.scale}"
        }
    }

    operator fun plus(other: Money): Money {
        require(currency == other.currency) { "Currency mismatch: $currency vs ${other.currency}" }
        return Money(amount.add(other.amount), currency)
    }

    operator fun minus(other: Money): Money {
        require(currency == other.currency) { "Currency mismatch: $currency vs ${other.currency}" }
        return Money(amount.subtract(other.amount), currency)
    }

    operator fun times(quantity: Int): Money =
        Money(amount.multiply(BigDecimal(quantity)), currency)

    companion object {
        fun zero(currency: Currency) = Money(BigDecimal.ZERO.setScale(currency.scale), currency)
    }
}
```

When embedding in entities, use `@AttributeOverrides` for column naming:
```kotlin
@Embedded
@AttributeOverrides(
    AttributeOverride(name = "amount", column = Column(name = "base_price_amount")),
    AttributeOverride(name = "currency", column = Column(name = "base_price_currency")),
)
val basePrice: Money
```

## Relationships

```kotlin
// Parent side
@OneToMany(mappedBy = "subscription", cascade = [CascadeType.ALL], orphanRemoval = true)
val invoices: MutableList<Invoice> = mutableListOf()

// Child side
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "subscription_id", nullable = false)
val subscription: Subscription
```

- `orphanRemoval = true` for owned collections
- Always set both sides of bidirectional relationships
- Helper methods: `fun addInvoice(invoice: Invoice) { invoices.add(invoice) }`

## Repository Interfaces

Extend `JpaRepository`. Use `@Query` with JPQL for custom lookups. Use `JOIN FETCH` to prevent N+1.

```kotlin
interface SubscriptionRepository : JpaRepository<Subscription, Long> {
    fun findByCustomerId(customerId: String): List<Subscription>

    @Query("SELECT s FROM Subscription s JOIN FETCH s.plan WHERE s.id = :id")
    fun findByIdWithPlan(@Param("id") id: Long): Subscription?

    @Query("SELECT s FROM Subscription s WHERE s.status = :status")
    fun findByStatus(@Param("status") status: SubscriptionStatus, pageable: Pageable): Page<Subscription>
}
```

## N+1 Prevention

- Use `JOIN FETCH` in `@Query` for known association access
- Use `@EntityGraph(attributePaths = ["plan", "invoices"])` for declarative fetching
- Avoid accessing lazy collections outside of transaction scope

## Entity Business Rule Validation (MUST follow)

- ALL business rules stated in the spec for an entity MUST be enforced in the entity's `init` block or constructor
- Do NOT defer entity-level validation to the service layer — entities protect their own invariants
- Example: If spec says "FREE tier plans must have base price of zero":
  ```kotlin
  init {
      if (tier == PlanTier.FREE) {
          require(basePrice.amount.compareTo(BigDecimal.ZERO) == 0) {
              "FREE tier plans must have zero base price"
          }
      }
  }
  ```

## Transaction Best Practices

- `@Transactional` on service methods, not on repository methods
- `@Transactional(readOnly = true)` enables Hibernate flush-mode MANUAL optimization
- Keep transactions short — avoid external calls inside transactions
