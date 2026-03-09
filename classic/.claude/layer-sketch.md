# Layer Sketch: Subscription Management API (Phase 0)

## Base Package
`com.wakita181009.classic`

## Source Root
`/Users/tetsuyawakita/IdeaProjects/clean-architecture-kotlin-in-practice/classic`

---

## 1. Enums

### PlanTier
- `FREE, STARTER, PROFESSIONAL, ENTERPRISE`
- Ordered by rank (ordinal). `isUpgradeTo(other)` = this.ordinal < other.ordinal
- File: `src/main/kotlin/com/wakita181009/classic/model/PlanTier.kt`

### BillingInterval
- `MONTHLY, YEARLY`
- `fun addTo(instant: Instant): Instant` — adds 1 month or 1 year
- File: `src/main/kotlin/com/wakita181009/classic/model/BillingInterval.kt`

### SubscriptionStatus
- `TRIAL, ACTIVE, PAUSED, PAST_DUE, CANCELED, EXPIRED`
- `fun canTransitionTo(target): Boolean` — per spec section 3.1
- Terminal: CANCELED, EXPIRED
- File: `src/main/kotlin/com/wakita181009/classic/model/SubscriptionStatus.kt`

### InvoiceStatus
- `DRAFT, OPEN, PAID, VOID, UNCOLLECTIBLE`
- `fun canTransitionTo(target): Boolean` — per spec section 3.2
- Terminal: PAID, VOID, UNCOLLECTIBLE
- File: `src/main/kotlin/com/wakita181009/classic/model/InvoiceStatus.kt`

### LineItemType
- `PLAN_CHARGE, USAGE_CHARGE, PRORATION_CREDIT, PRORATION_CHARGE`
- File: `src/main/kotlin/com/wakita181009/classic/model/LineItemType.kt`

### DiscountType
- `PERCENTAGE, FIXED_AMOUNT`
- File: `src/main/kotlin/com/wakita181009/classic/model/DiscountType.kt`

---

## 2. Value Objects

### Money (`@Embeddable data class`)
- Fields: `amount: BigDecimal`, `currency: Currency`
- Inner enum `Currency(val scale: Int)`: USD(2), EUR(2), JPY(0)
- Init: validate JPY has scale=0, amount.scale() <= currency.scale
- Operators: `plus`, `minus`, `times(Int)`, `times(numerator: Long, denominator: Long)` (for proration with HALF_UP rounding), `negate()`
- Companion: `zero(currency)`
- Negative amounts allowed (for credits/proration)
- File: `src/main/kotlin/com/wakita181009/classic/model/Money.kt`

---

## 3. JPA Entities

### Plan (`@Entity class`)
- Fields: id(Long, @Id @GeneratedValue), name(String), billingInterval(BillingInterval), basePrice(Money @Embedded), usageLimit(Int?), features(Set<String> @ElementCollection), tier(PlanTier), active(Boolean)
- Init block: FREE tier must have zero basePrice; non-FREE must have positive basePrice
- File: `src/main/kotlin/com/wakita181009/classic/model/Plan.kt`

### Subscription (`@Entity class`)
- Fields: id, customerId(Long), plan(@ManyToOne LAZY), status(SubscriptionStatus), currentPeriodStart(Instant), currentPeriodEnd(Instant), trialStart(Instant?), trialEnd(Instant?), pausedAt(Instant?), canceledAt(Instant?), cancelAtPeriodEnd(Boolean), gracePeriodEnd(Instant?), pauseCount(Int), createdAt(Instant), updatedAt(Instant)
- @OneToMany to invoices, usageRecords, discount
- Methods: `transitionTo(status)` validates via canTransitionTo
- File: `src/main/kotlin/com/wakita181009/classic/model/Subscription.kt`

### Invoice (`@Entity class`)
- Fields: id, subscription(@ManyToOne LAZY), lineItems(@OneToMany), subtotal(Money @Embedded), discountAmount(Money @Embedded), total(Money @Embedded), currency(String), status(InvoiceStatus), dueDate(LocalDate), paidAt(Instant?), paymentAttemptCount(Int), createdAt(Instant), updatedAt(Instant)
- Methods: `transitionTo(status)` validates via canTransitionTo
- File: `src/main/kotlin/com/wakita181009/classic/model/Invoice.kt`

### InvoiceLineItem (`@Entity class`)
- Fields: id, invoice(@ManyToOne LAZY), description(String), amount(Money @Embedded), type(LineItemType)
- File: `src/main/kotlin/com/wakita181009/classic/model/InvoiceLineItem.kt`

### UsageRecord (`@Entity class`)
- Fields: id, subscription(@ManyToOne LAZY), metricName(String), quantity(Int), recordedAt(Instant), idempotencyKey(String @Column unique)
- File: `src/main/kotlin/com/wakita181009/classic/model/UsageRecord.kt`

### Discount (`@Entity class`)
- Fields: id, subscription(@ManyToOne LAZY), type(DiscountType), value(BigDecimal), durationMonths(Int?), remainingCycles(Int?), appliedAt(Instant)
- Init: PERCENTAGE value 1..100; FIXED_AMOUNT value > 0; durationMonths 1..24 or null
- File: `src/main/kotlin/com/wakita181009/classic/model/Discount.kt`

---

## 4. Repository Interfaces

### PlanRepository
- `JpaRepository<Plan, Long>`
- `findByIdAndActiveTrue(id: Long): Plan?`
- File: `src/main/kotlin/com/wakita181009/classic/repository/PlanRepository.kt`

### SubscriptionRepository
- `JpaRepository<Subscription, Long>`
- `findByCustomerIdAndStatusIn(customerId: Long, statuses: List<SubscriptionStatus>): List<Subscription>`
- `findByIdWithPlan(id: Long): Subscription?` (JOIN FETCH plan)
- `findByCustomerId(customerId: Long): List<Subscription>`
- `findByStatusAndCurrentPeriodEndBefore(status: SubscriptionStatus, time: Instant): List<Subscription>`
- `findByStatusAndPausedAtBefore(status: SubscriptionStatus, time: Instant): List<Subscription>`
- File: `src/main/kotlin/com/wakita181009/classic/repository/SubscriptionRepository.kt`

### InvoiceRepository
- `JpaRepository<Invoice, Long>`
- `findBySubscriptionId(subscriptionId: Long): List<Invoice>`
- `findBySubscriptionIdAndStatus(subscriptionId: Long, status: InvoiceStatus): List<Invoice>`
- File: `src/main/kotlin/com/wakita181009/classic/repository/InvoiceRepository.kt`

### UsageRecordRepository
- `JpaRepository<UsageRecord, Long>`
- `findByIdempotencyKey(key: String): UsageRecord?`
- `findBySubscriptionIdAndRecordedAtBetween(subscriptionId: Long, start: Instant, end: Instant): List<UsageRecord>`
- `sumQuantityBySubscriptionIdAndRecordedAtBetween(...)` or use query
- File: `src/main/kotlin/com/wakita181009/classic/repository/UsageRecordRepository.kt`

### DiscountRepository
- `JpaRepository<Discount, Long>`
- `findBySubscriptionId(subscriptionId: Long): Discount?`
- File: `src/main/kotlin/com/wakita181009/classic/repository/DiscountRepository.kt`

---

## 5. Exception Hierarchy

### ServiceException (base)
- `open class ServiceException(message: String, val status: HttpStatus)`
- File: `src/main/kotlin/com/wakita181009/classic/exception/ServiceException.kt`

### Specific Exceptions (all in same file or separate)
- `PlanNotFoundException(id: Long)` — 404
- `SubscriptionNotFoundException(id: Long)` — 404
- `InvoiceNotFoundException(id: Long)` — 404
- `InvalidStateTransitionException(from: String, to: String)` — 409
- `BusinessRuleViolationException(message: String)` — 409
- `PaymentFailedException(message: String)` — 502
- `DuplicateSubscriptionException(customerId: Long)` — 409
- `InvalidDiscountCodeException(code: String)` — 400
- File: `src/main/kotlin/com/wakita181009/classic/exception/Exceptions.kt`

### GlobalExceptionHandler
- `@RestControllerAdvice`
- Handles: ServiceException, MethodArgumentNotValidException, ConstraintViolationException, IllegalArgumentException, Exception
- File: `src/main/kotlin/com/wakita181009/classic/exception/GlobalExceptionHandler.kt`

### ErrorResponse
- `data class ErrorResponse(val message: String, val status: Int)`
- File: `src/main/kotlin/com/wakita181009/classic/exception/ErrorResponse.kt`

---

## 6. Service Classes

### PaymentGateway (interface)
- `fun charge(amount: Money, paymentMethod: String, customerRef: Long): PaymentResult`
- `fun refund(transactionId: String, amount: Money): RefundResult`
- `data class PaymentResult(val success: Boolean, val transactionId: String?, val processedAt: Instant?, val errorReason: String?)`
- `data class RefundResult(val success: Boolean, val errorReason: String?)`
- File: `src/main/kotlin/com/wakita181009/classic/service/PaymentGateway.kt`

### SubscriptionService
- Dependencies: SubscriptionRepository, PlanRepository, InvoiceRepository, UsageRecordRepository, DiscountRepository, PaymentGateway, Clock
- Methods: createSubscription, changePlan, pauseSubscription, resumeSubscription, cancelSubscription, processRenewal, getSubscription, listByCustomerId
- File: `src/main/kotlin/com/wakita181009/classic/service/SubscriptionService.kt`

### UsageService
- Dependencies: SubscriptionRepository, UsageRecordRepository, Clock
- Methods: recordUsage
- File: `src/main/kotlin/com/wakita181009/classic/service/UsageService.kt`

### InvoiceService
- Dependencies: InvoiceRepository, SubscriptionRepository, PaymentGateway, Clock
- Methods: recoverPayment, listBySubscriptionId
- File: `src/main/kotlin/com/wakita181009/classic/service/InvoiceService.kt`

---

## 7. DTOs

### Request DTOs
- `CreateSubscriptionRequest(customerId: Long, planId: Long, paymentMethod: String, discountCode: String?)`
- `ChangePlanRequest(newPlanId: Long)`
- `CancelSubscriptionRequest(immediate: Boolean = false)`
- `RecordUsageRequest(metricName: String, quantity: Int, idempotencyKey: String)`
- File: `src/main/kotlin/com/wakita181009/classic/dto/Requests.kt`

### Response DTOs
- `SubscriptionResponse` with `companion object { fun from(entity) }`
- `InvoiceResponse` with line items
- `UsageRecordResponse`
- `PlanResponse` (nested in SubscriptionResponse)
- `MoneyResponse(amount: BigDecimal, currency: String)`
- `DiscountResponse`
- File: `src/main/kotlin/com/wakita181009/classic/dto/Responses.kt`

---

## 8. Controllers

### SubscriptionController
- `@RestController @RequestMapping("/api/subscriptions")`
- POST / — createSubscription (201)
- GET /{id} — getSubscription (200)
- GET ?customerId={id} — listByCustomer (200)
- PUT /{id}/plan — changePlan (200)
- POST /{id}/pause — pause (200)
- POST /{id}/resume — resume (200)
- POST /{id}/cancel — cancel (200)
- POST /{id}/usage — recordUsage (201)
- File: `src/main/kotlin/com/wakita181009/classic/controller/SubscriptionController.kt`

### InvoiceController
- `@RestController @RequestMapping("/api/invoices")`
- POST /{id}/pay — recoverPayment (200)
- GET ?subscriptionId={id} — listInvoices (200)
- File: `src/main/kotlin/com/wakita181009/classic/controller/InvoiceController.kt`

---

## 9. Configuration

### ClockConfig
- `@Configuration` providing `Clock.systemUTC()` bean
- File: `src/main/kotlin/com/wakita181009/classic/config/ClockConfig.kt`

---

## 10. Test Configuration

### application-test.yaml (or application.yaml in test resources)
- H2 in-memory database config
- File: `src/test/resources/application.yaml`

---

## Files to Create

### Main sources (src/main/kotlin/com/wakita181009/classic/)

| # | Path | Type |
|---|------|------|
| 1 | model/PlanTier.kt | Enum |
| 2 | model/BillingInterval.kt | Enum |
| 3 | model/SubscriptionStatus.kt | Enum |
| 4 | model/InvoiceStatus.kt | Enum |
| 5 | model/LineItemType.kt | Enum |
| 6 | model/DiscountType.kt | Enum |
| 7 | model/Money.kt | Value Object |
| 8 | model/Plan.kt | Entity |
| 9 | model/Subscription.kt | Entity |
| 10 | model/Invoice.kt | Entity |
| 11 | model/InvoiceLineItem.kt | Entity |
| 12 | model/UsageRecord.kt | Entity |
| 13 | model/Discount.kt | Entity |
| 14 | repository/PlanRepository.kt | Repository |
| 15 | repository/SubscriptionRepository.kt | Repository |
| 16 | repository/InvoiceRepository.kt | Repository |
| 17 | repository/UsageRecordRepository.kt | Repository |
| 18 | repository/DiscountRepository.kt | Repository |
| 19 | exception/ServiceException.kt | Exception base |
| 20 | exception/Exceptions.kt | Specific exceptions |
| 21 | exception/GlobalExceptionHandler.kt | ControllerAdvice |
| 22 | exception/ErrorResponse.kt | DTO |
| 23 | service/PaymentGateway.kt | Interface |
| 24 | service/SubscriptionService.kt | Service |
| 25 | service/UsageService.kt | Service |
| 26 | service/InvoiceService.kt | Service |
| 27 | dto/Requests.kt | Request DTOs |
| 28 | dto/Responses.kt | Response DTOs |
| 29 | controller/SubscriptionController.kt | Controller |
| 30 | controller/InvoiceController.kt | Controller |
| 31 | config/ClockConfig.kt | Config |

### Test sources (src/test/kotlin/com/wakita181009/classic/)

| # | Path | Type |
|---|------|------|
| 32 | model/MoneyTest.kt | Unit test |
| 33 | model/PlanTierTest.kt | Unit test |
| 34 | model/BillingIntervalTest.kt | Unit test |
| 35 | model/SubscriptionStatusTest.kt | Unit test |
| 36 | model/InvoiceStatusTest.kt | Unit test |
| 37 | model/PlanTest.kt | Unit test |
| 38 | model/DiscountTest.kt | Unit test |
| 39 | service/SubscriptionServiceTest.kt | Service test |
| 40 | service/UsageServiceTest.kt | Service test |
| 41 | service/InvoiceServiceTest.kt | Service test |
| 42 | controller/SubscriptionControllerTest.kt | Controller test |
| 43 | controller/InvoiceControllerTest.kt | Controller test |
| 44 | integration/SubscriptionIntegrationTest.kt | Integration test |

### Test resources

| # | Path | Type |
|---|------|------|
| 45 | src/test/resources/application.yaml | Test config |
