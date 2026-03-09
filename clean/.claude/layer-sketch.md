# Layer Sketch: Subscription Management API

Base package: `com.wakita181009.clean`

## Domain Layer

### Value Objects (`domain/model/`)

| File | Type | Details |
|------|------|---------|
| `Money.kt` | data class | amount: BigDecimal, currency: Currency. Arithmetic: +, -, *, negate. HALF_UP rounding. JPY scale=0, USD/EUR scale=2. Negative allowed (credits). |
| `Currency.kt` | enum class | USD, EUR, JPY. Each has `scale: Int`. |
| `SubscriptionId.kt` | @JvmInline value class | Long, must be > 0. Private ctor + companion invoke returning Either. |
| `CustomerId.kt` | @JvmInline value class | Long, must be > 0. |
| `PlanId.kt` | @JvmInline value class | Long, must be > 0. |
| `InvoiceId.kt` | @JvmInline value class | Long, must be > 0. |
| `UsageId.kt` | @JvmInline value class | Long, must be > 0. |
| `DiscountId.kt` | @JvmInline value class | Long, must be > 0. |
| `MetricName.kt` | @JvmInline value class | String, non-blank. |
| `IdempotencyKey.kt` | @JvmInline value class | String, non-blank. |
| `BillingInterval.kt` | enum class | MONTHLY, YEARLY. Method: `nextPeriodEnd(start: Instant): Instant`. |
| `PlanTier.kt` | enum class | FREE(0), STARTER(1), PROFESSIONAL(2), ENTERPRISE(3). `rank: Int`. `isUpgradeFrom(other)`, `isDowngradeFrom(other)`. |
| `PaymentMethod.kt` | enum class | CREDIT_CARD, BANK_TRANSFER, etc. |
| `InvoiceLineItemType.kt` | enum class | PLAN_CHARGE, USAGE_CHARGE, PRORATION_CREDIT, PRORATION_CHARGE |
| `DiscountType.kt` | enum class | PERCENTAGE, FIXED_AMOUNT |

### Entities (`domain/model/`)

| File | Type | Details |
|------|------|---------|
| `Plan.kt` | data class | id: PlanId, name: String, billingInterval: BillingInterval, basePrice: Money, usageLimit: Int?, features: Set<String>, tier: PlanTier, active: Boolean. Validation: FREE tier => price zero; non-FREE => price > 0. |
| `Subscription.kt` | data class | id: SubscriptionId?, customerId: CustomerId, plan: Plan, status: SubscriptionStatus, currentPeriodStart: Instant, currentPeriodEnd: Instant, trialStart: Instant?, trialEnd: Instant?, pausedAt: Instant?, canceledAt: Instant?, cancelAtPeriodEnd: Boolean, gracePeriodEnd: Instant?, pauseCountInPeriod: Int, paymentMethod: PaymentMethod?, createdAt: Instant, updatedAt: Instant |
| `Invoice.kt` | data class | id: InvoiceId?, subscriptionId: SubscriptionId, lineItems: List<InvoiceLineItem>, subtotal: Money, discountAmount: Money, total: Money, currency: Currency, status: InvoiceStatus, dueDate: LocalDate, paidAt: Instant?, paymentAttemptCount: Int, createdAt: Instant, updatedAt: Instant |
| `InvoiceLineItem.kt` | data class | description: String, amount: Money, type: InvoiceLineItemType |
| `UsageRecord.kt` | data class | id: UsageId?, subscriptionId: SubscriptionId, metricName: MetricName, quantity: Int, recordedAt: Instant, idempotencyKey: IdempotencyKey |
| `Discount.kt` | data class | id: DiscountId?, subscriptionId: SubscriptionId, type: DiscountType, value: BigDecimal, fixedAmountMoney: Money?, durationMonths: Int?, remainingCycles: Int?, appliedAt: Instant |

### State Machines (`domain/model/`)

| File | Type | Details |
|------|------|---------|
| `SubscriptionStatus.kt` | sealed interface | Trial, Active, Paused, PastDue, Canceled, Expired. Each state has only valid transition methods. |
| `InvoiceStatus.kt` | sealed interface | Draft, Open, Paid, Void, Uncollectible. Each state has only valid transition methods. |

### Domain Errors (`domain/error/`)

| File | Type | Details |
|------|------|---------|
| `MoneyError.kt` | sealed interface : DomainError | CurrencyMismatch, InvalidJpyScale |
| `SubscriptionError.kt` | sealed interface : DomainError | InvalidTransition, PauseLimitReached, etc. |
| `InvoiceError.kt` | sealed interface : DomainError | InvalidTransition, etc. |
| `PlanError.kt` | sealed interface : DomainError | InvalidPriceForTier, etc. |

### Domain Services (`domain/service/`)

| File | Type | Details |
|------|------|---------|
| `ProrationDomainService.kt` | class | calculateProration(currentPlan, newPlan, daysRemaining, totalDays): Either<DomainError, ProrationResult>. ProrationResult has credit, charge, netAmount line items. |

### Domain Repositories (`domain/repository/`) -- write-only

| File | Type | Methods |
|------|------|---------|
| `SubscriptionRepository.kt` | interface | save(Subscription): Either<DomainError, Subscription> |
| `InvoiceRepository.kt` | interface | save(Invoice): Either<DomainError, Invoice> |
| `UsageRecordRepository.kt` | interface | save(UsageRecord): Either<DomainError, UsageRecord> |
| `DiscountRepository.kt` | interface | save(Discount): Either<DomainError, Discount> |

---

## Application Layer

### Shared Ports (`application/port/`)

| File | Type | Details |
|------|------|---------|
| `ClockPort.kt` | interface | now(): Instant |
| `TransactionPort.kt` | interface | (already exists) run<T>(block: () -> T): T |

### Command-Side Ports (`application/command/port/`)

| File | Type | Details |
|------|------|---------|
| `SubscriptionCommandQueryPort.kt` | interface | findById(SubscriptionId): Either<..., Subscription>, findActiveByCustomerId(CustomerId): Either<..., Subscription?>, findByIdWithDiscount(SubscriptionId): Either<..., Pair<Subscription, Discount?>> |
| `PaymentGatewayPort.kt` | interface | charge(amount: Money, paymentMethod: PaymentMethod, customerRef: String): Either<PaymentError, PaymentResult> |
| `PlanQueryPort.kt` | interface | findById(PlanId): Either<..., Plan> |
| `DiscountCodePort.kt` | interface | resolve(code: String): Either<..., Discount> |
| `InvoiceCommandQueryPort.kt` | interface | findById(InvoiceId): Either<..., Invoice>, findOpenBySubscriptionId(SubscriptionId): Either<..., List<Invoice>> |
| `UsageQueryPort.kt` | interface | sumQuantityForPeriod(subscriptionId, periodStart, periodEnd): Either<..., Long>, findByIdempotencyKey(key): Either<..., UsageRecord?>, findForPeriod(subscriptionId, periodStart, periodEnd): Either<..., List<UsageRecord>> |

### Command DTOs (`application/command/dto/`)

| File | Type | Details |
|------|------|---------|
| `CreateSubscriptionCommand.kt` | data class | customerId: Long, planId: Long, paymentMethod: String, discountCode: String? |
| `ChangePlanCommand.kt` | data class | subscriptionId: Long, newPlanId: Long |
| `RecordUsageCommand.kt` | data class | subscriptionId: Long, metricName: String, quantity: Int, idempotencyKey: String |
| `CancelSubscriptionCommand.kt` | data class | subscriptionId: Long, immediate: Boolean |
| `PaymentResult.kt` | data class | transactionId: String, processedAt: Instant |
| `PaymentError.kt` | sealed interface | Declined, Timeout, InsufficientFunds, Unknown |

### Command Errors (`application/command/error/`)

| File | Type | Details |
|------|------|---------|
| `SubscriptionCreateError.kt` | sealed interface : ApplicationError | InvalidInput(field, reason), PlanNotFound, PlanInactive, AlreadySubscribed, InvalidDiscountCode, Domain(DomainError), Internal(cause) |
| `PlanChangeError.kt` | sealed interface : ApplicationError | InvalidInput, SubscriptionNotFound, NotActive, SamePlan, PlanNotFound, PlanInactive, CurrencyMismatch, PaymentFailed, Domain(DomainError), Internal |
| `PauseSubscriptionError.kt` | sealed interface : ApplicationError | InvalidInput, SubscriptionNotFound, NotActive, PauseLimitReached, Domain(DomainError), Internal |
| `ResumeSubscriptionError.kt` | sealed interface : ApplicationError | InvalidInput, SubscriptionNotFound, NotPaused, Domain(DomainError), Internal |
| `CancelSubscriptionError.kt` | sealed interface : ApplicationError | InvalidInput, SubscriptionNotFound, AlreadyTerminal, CannotEndOfPeriodForPaused, Domain(DomainError), Internal |
| `ProcessRenewalError.kt` | sealed interface : ApplicationError | SubscriptionNotFound, NotDue, Domain(DomainError), Internal |
| `RecordUsageError.kt` | sealed interface : ApplicationError | InvalidInput, SubscriptionNotFound, NotActive, UsageLimitExceeded, Domain(DomainError), Internal |
| `RecoverPaymentError.kt` | sealed interface : ApplicationError | InvalidInput, InvoiceNotFound, InvoiceNotOpen, SubscriptionNotPastDue, GracePeriodExpired, PaymentFailed, Domain(DomainError), Internal |

### Command Use Cases (`application/command/usecase/`)

| File | Interface | Impl | Method |
|------|-----------|------|--------|
| `SubscriptionCreateUseCase.kt` | SubscriptionCreateUseCase | SubscriptionCreateUseCaseImpl | execute(CreateSubscriptionCommand): Either<SubscriptionCreateError, Subscription> |
| `PlanChangeUseCase.kt` | PlanChangeUseCase | PlanChangeUseCaseImpl | execute(ChangePlanCommand): Either<PlanChangeError, Subscription> |
| `PauseSubscriptionUseCase.kt` | PauseSubscriptionUseCase | PauseSubscriptionUseCaseImpl | execute(subscriptionId: Long): Either<PauseSubscriptionError, Subscription> |
| `ResumeSubscriptionUseCase.kt` | ResumeSubscriptionUseCase | ResumeSubscriptionUseCaseImpl | execute(subscriptionId: Long): Either<ResumeSubscriptionError, Subscription> |
| `CancelSubscriptionUseCase.kt` | CancelSubscriptionUseCase | CancelSubscriptionUseCaseImpl | execute(CancelSubscriptionCommand): Either<CancelSubscriptionError, Subscription> |
| `ProcessRenewalUseCase.kt` | ProcessRenewalUseCase | ProcessRenewalUseCaseImpl | execute(subscriptionId: Long): Either<ProcessRenewalError, Subscription> |
| `RecordUsageUseCase.kt` | RecordUsageUseCase | RecordUsageUseCaseImpl | execute(RecordUsageCommand): Either<RecordUsageError, UsageRecord> |
| `RecoverPaymentUseCase.kt` | RecoverPaymentUseCase | RecoverPaymentUseCaseImpl | execute(invoiceId: Long): Either<RecoverPaymentError, Invoice> |

### Query DTOs (`application/query/dto/`)

| File | Type | Fields (flat primitives) |
|------|------|---------|
| `SubscriptionDto.kt` | data class | id, customerId, planId, planName, planTier, planBillingInterval, planBasePriceAmount, planBasePriceCurrency, status, currentPeriodStart, currentPeriodEnd, trialEnd, pausedAt, canceledAt, cancelAtPeriodEnd, discountType, discountValue, discountRemainingCycles, createdAt, updatedAt |
| `InvoiceDto.kt` | data class | id, subscriptionId, lineItems: List<InvoiceLineItemDto>, subtotalAmount, subtotalCurrency, discountAmount, totalAmount, totalCurrency, status, dueDate, paidAt, paymentAttemptCount, createdAt |
| `InvoiceLineItemDto.kt` | data class | description, amount, currency, type |

### Query Errors (`application/query/error/`)

| File | Type |
|------|------|
| `SubscriptionFindByIdQueryError.kt` | sealed interface : ApplicationError | InvalidInput, NotFound, Internal |
| `SubscriptionListByCustomerQueryError.kt` | sealed interface : ApplicationError | InvalidInput, Internal |
| `InvoiceListBySubscriptionQueryError.kt` | sealed interface : ApplicationError | InvalidInput, Internal |

### Query Repositories (`application/query/repository/`)

| File | Type |
|------|------|
| `SubscriptionQueryRepository.kt` | interface | findById(Long): Either<..., SubscriptionDto>, listByCustomerId(Long): Either<..., List<SubscriptionDto>> |
| `InvoiceQueryRepository.kt` | interface | listBySubscriptionId(Long): Either<..., List<InvoiceDto>> |

### Query Use Cases (`application/query/usecase/`)

| File | Interface | Impl |
|------|-----------|------|
| `SubscriptionFindByIdQueryUseCase.kt` | SubscriptionFindByIdQueryUseCase | SubscriptionFindByIdQueryUseCaseImpl | execute(id: Long): Either<SubscriptionFindByIdQueryError, SubscriptionDto> |
| `SubscriptionListByCustomerQueryUseCase.kt` | SubscriptionListByCustomerQueryUseCase | SubscriptionListByCustomerQueryUseCaseImpl | execute(customerId: Long): Either<..., List<SubscriptionDto>> |
| `InvoiceListBySubscriptionQueryUseCase.kt` | InvoiceListBySubscriptionQueryUseCase | InvoiceListBySubscriptionQueryUseCaseImpl | execute(subscriptionId: Long): Either<..., List<InvoiceDto>> |

---

## Infrastructure Layer

### Command Repositories (`infrastructure/command/repository/`)

| File | Implements | Details |
|------|-----------|---------|
| `SubscriptionRepositoryImpl.kt` | SubscriptionRepository + SubscriptionCommandQueryPort | jOOQ JDBC, maps to/from domain entities |
| `InvoiceRepositoryImpl.kt` | InvoiceRepository + InvoiceCommandQueryPort | jOOQ JDBC |
| `UsageRecordRepositoryImpl.kt` | UsageRecordRepository + UsageQueryPort | jOOQ JDBC |
| `DiscountRepositoryImpl.kt` | DiscountRepository + DiscountCodePort | jOOQ JDBC |

### Query Repositories (`infrastructure/query/repository/`)

| File | Implements | Details |
|------|-----------|---------|
| `SubscriptionQueryRepositoryImpl.kt` | SubscriptionQueryRepository | jOOQ JDBC → SubscriptionDto |
| `InvoiceQueryRepositoryImpl.kt` | InvoiceQueryRepository | jOOQ JDBC → InvoiceDto |

### Adapters (`infrastructure/command/adapter/`)

| File | Implements | Details |
|------|-----------|---------|
| `ClockAdapter.kt` | ClockPort | java.time.Clock → Instant.now(clock) |
| `PaymentGatewayAdapter.kt` | PaymentGatewayPort | Stub implementation (always succeeds or configurable) |
| `PlanQueryAdapter.kt` | PlanQueryPort | jOOQ JDBC → Plan domain entity |

Note: TransactionAdapter already exists at `infrastructure/adapter/TransactionAdapter.kt`.

---

## Presentation Layer

### Controllers (`presentation/controller/`)

| File | Type | Endpoints |
|------|------|-----------|
| `SubscriptionController.kt` | @RestController | POST /api/subscriptions, PUT /api/subscriptions/{id}/plan, POST /api/subscriptions/{id}/pause, POST /api/subscriptions/{id}/resume, POST /api/subscriptions/{id}/cancel, POST /api/subscriptions/{id}/usage, GET /api/subscriptions/{id}, GET /api/subscriptions?customerId={id} |
| `InvoiceController.kt` | @RestController | POST /api/invoices/{id}/pay, GET /api/invoices?subscriptionId={id} |

### Request/Response DTOs (`presentation/dto/`)

| File | Type |
|------|------|
| `CreateSubscriptionRequest.kt` | data class | customerId, planId, paymentMethod, discountCode |
| `ChangePlanRequest.kt` | data class | newPlanId |
| `CancelSubscriptionRequest.kt` | data class | immediate |
| `RecordUsageRequest.kt` | data class | metricName, quantity, idempotencyKey |
| `SubscriptionResponse.kt` | data class | Full JSON response shape |
| `InvoiceResponse.kt` | data class | Full JSON response shape |
| `UsageRecordResponse.kt` | data class | id, subscriptionId, metricName, quantity, recordedAt, idempotencyKey |
| `ErrorResponse.kt` | data class | message |

---

## Framework Layer

### Config (`framework/config/`)

| File | Type | Details |
|------|------|---------|
| `UseCaseConfig.kt` | @Configuration | Wires all command + query use cases with their dependencies |
| `DomainServiceConfig.kt` | @Configuration | Wires ProrationDomainService |

---

## File List (Full Paths)

Base: `com/wakita181009/clean`

### Domain (`domain/src/main/kotlin/{base}/domain/`)

1. `model/Currency.kt`
2. `model/Money.kt`
3. `model/SubscriptionId.kt`
4. `model/CustomerId.kt`
5. `model/PlanId.kt`
6. `model/InvoiceId.kt`
7. `model/UsageId.kt`
8. `model/DiscountId.kt`
9. `model/MetricName.kt`
10. `model/IdempotencyKey.kt`
11. `model/BillingInterval.kt`
12. `model/PlanTier.kt`
13. `model/PaymentMethod.kt`
14. `model/InvoiceLineItemType.kt`
15. `model/DiscountType.kt`
16. `model/SubscriptionStatus.kt`
17. `model/InvoiceStatus.kt`
18. `model/Plan.kt`
19. `model/Subscription.kt`
20. `model/Invoice.kt`
21. `model/InvoiceLineItem.kt`
22. `model/UsageRecord.kt`
23. `model/Discount.kt`
24. `error/MoneyError.kt`
25. `error/SubscriptionError.kt`
26. `error/InvoiceError.kt`
27. `error/PlanError.kt`
28. `service/ProrationDomainService.kt`
29. `repository/SubscriptionRepository.kt`
30. `repository/InvoiceRepository.kt`
31. `repository/UsageRecordRepository.kt`
32. `repository/DiscountRepository.kt`

### Application (`application/src/main/kotlin/{base}/application/`)

33. `port/ClockPort.kt`
34. `command/port/SubscriptionCommandQueryPort.kt`
35. `command/port/PaymentGatewayPort.kt`
36. `command/port/PlanQueryPort.kt`
37. `command/port/DiscountCodePort.kt`
38. `command/port/InvoiceCommandQueryPort.kt`
39. `command/port/UsageQueryPort.kt`
40. `command/dto/CreateSubscriptionCommand.kt`
41. `command/dto/ChangePlanCommand.kt`
42. `command/dto/RecordUsageCommand.kt`
43. `command/dto/CancelSubscriptionCommand.kt`
44. `command/dto/PaymentResult.kt`
45. `command/dto/PaymentError.kt`
46. `command/error/SubscriptionCreateError.kt`
47. `command/error/PlanChangeError.kt`
48. `command/error/PauseSubscriptionError.kt`
49. `command/error/ResumeSubscriptionError.kt`
50. `command/error/CancelSubscriptionError.kt`
51. `command/error/ProcessRenewalError.kt`
52. `command/error/RecordUsageError.kt`
53. `command/error/RecoverPaymentError.kt`
54. `command/usecase/SubscriptionCreateUseCase.kt`
55. `command/usecase/PlanChangeUseCase.kt`
56. `command/usecase/PauseSubscriptionUseCase.kt`
57. `command/usecase/ResumeSubscriptionUseCase.kt`
58. `command/usecase/CancelSubscriptionUseCase.kt`
59. `command/usecase/ProcessRenewalUseCase.kt`
60. `command/usecase/RecordUsageUseCase.kt`
61. `command/usecase/RecoverPaymentUseCase.kt`
62. `query/dto/SubscriptionDto.kt`
63. `query/dto/InvoiceDto.kt`
64. `query/dto/InvoiceLineItemDto.kt`
65. `query/error/SubscriptionFindByIdQueryError.kt`
66. `query/error/SubscriptionListByCustomerQueryError.kt`
67. `query/error/InvoiceListBySubscriptionQueryError.kt`
68. `query/repository/SubscriptionQueryRepository.kt`
69. `query/repository/InvoiceQueryRepository.kt`
70. `query/usecase/SubscriptionFindByIdQueryUseCase.kt`
71. `query/usecase/SubscriptionListByCustomerQueryUseCase.kt`
72. `query/usecase/InvoiceListBySubscriptionQueryUseCase.kt`

### Infrastructure (`infrastructure/src/main/kotlin/{base}/infrastructure/`)

73. `command/repository/SubscriptionRepositoryImpl.kt`
74. `command/repository/InvoiceRepositoryImpl.kt`
75. `command/repository/UsageRecordRepositoryImpl.kt`
76. `command/repository/DiscountRepositoryImpl.kt`
77. `command/adapter/ClockAdapter.kt`
78. `command/adapter/PaymentGatewayAdapter.kt`
79. `command/adapter/PlanQueryAdapter.kt`
80. `query/repository/SubscriptionQueryRepositoryImpl.kt`
81. `query/repository/InvoiceQueryRepositoryImpl.kt`

### Presentation (`presentation/src/main/kotlin/{base}/presentation/`)

82. `controller/SubscriptionController.kt`
83. `controller/InvoiceController.kt`
84. `dto/CreateSubscriptionRequest.kt`
85. `dto/ChangePlanRequest.kt`
86. `dto/CancelSubscriptionRequest.kt`
87. `dto/RecordUsageRequest.kt`
88. `dto/SubscriptionResponse.kt`
89. `dto/InvoiceResponse.kt`
90. `dto/UsageRecordResponse.kt`
91. `dto/ErrorResponse.kt`

### Framework (`framework/src/main/kotlin/{base}/framework/`)

92. `config/UseCaseConfig.kt`
93. `config/DomainServiceConfig.kt`

### Tests

#### Domain Tests (`domain/src/test/kotlin/{base}/domain/`)
94. `model/MoneyTest.kt` -- V-M1 through V-M12
95. `model/PlanTierTest.kt` -- V-T1 through V-T5
96. `model/BillingIntervalTest.kt` -- V-BI1 through V-BI5
97. `model/ValueObjectIdTest.kt` -- V-ID1 through V-ID4
98. `model/DiscountTest.kt` -- V-D1 through V-D11
99. `model/UsageRecordTest.kt` -- V-U1 through V-U6
100. `model/SubscriptionStatusTest.kt` -- S-V1 through S-V11, S-I1 through S-I13
101. `model/InvoiceStatusTest.kt` -- IS-V1 through IS-V5, IS-I1 through IS-I6
102. `service/ProrationDomainServiceTest.kt` -- CP-P1 through CP-P5

#### Application Tests (`application/src/test/kotlin/{base}/application/`)
103. `command/usecase/SubscriptionCreateUseCaseTest.kt` -- CS-H1 through CS-B8
104. `command/usecase/PlanChangeUseCaseTest.kt` -- CP-U1 through CP-E8
105. `command/usecase/PauseSubscriptionUseCaseTest.kt` -- PA-H1 through PA-E7
106. `command/usecase/ResumeSubscriptionUseCaseTest.kt` -- RE-H1 through RE-E6
107. `command/usecase/CancelSubscriptionUseCaseTest.kt` -- CA-H1 through CA-E5
108. `command/usecase/ProcessRenewalUseCaseTest.kt` -- RN-H1 through RN-S3
109. `command/usecase/RecordUsageUseCaseTest.kt` -- US-H1 through US-B6
110. `command/usecase/RecoverPaymentUseCaseTest.kt` -- RP-H1 through RP-E7
111. `query/usecase/SubscriptionFindByIdQueryUseCaseTest.kt`
112. `query/usecase/SubscriptionListByCustomerQueryUseCaseTest.kt`
113. `query/usecase/InvoiceListBySubscriptionQueryUseCaseTest.kt`

#### Presentation Tests (`presentation/src/test/kotlin/{base}/presentation/`)
114. `controller/SubscriptionControllerTest.kt` -- API tests
115. `controller/InvoiceControllerTest.kt` -- API tests
