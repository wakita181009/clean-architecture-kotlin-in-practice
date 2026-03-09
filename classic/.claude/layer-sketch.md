# Layer Sketch: Subscription Management API -- Phase 1

Phase 1 EXTENDS Phase 0. All existing files are modified in-place; new files are created for new domain concepts.

Base package: `com.wakita181009.classic`
Source root: `/Users/tetsuyawakita/IdeaProjects/clean-architecture-kotlin-in-practice/classic`

---

## 1. Enums (New)

### BillingType
- File: `src/main/kotlin/com/wakita181009/classic/model/BillingType.kt`
- Values: `FLAT`, `PER_SEAT`

### SubscriptionAddOnStatus
- File: `src/main/kotlin/com/wakita181009/classic/model/SubscriptionAddOnStatus.kt`
- Values: `ACTIVE`, `DETACHED`
- `canTransitionTo()`: ACTIVE -> DETACHED only. DETACHED is terminal.

### CreditNoteType
- File: `src/main/kotlin/com/wakita181009/classic/model/CreditNoteType.kt`
- Values: `FULL`, `PARTIAL`

### CreditApplication
- File: `src/main/kotlin/com/wakita181009/classic/model/CreditApplication.kt`
- Values: `REFUND_TO_PAYMENT`, `ACCOUNT_CREDIT`

### CreditNoteStatus
- File: `src/main/kotlin/com/wakita181009/classic/model/CreditNoteStatus.kt`
- Values: `ISSUED`, `APPLIED`, `VOIDED`
- `canTransitionTo()`: ISSUED -> APPLIED, ISSUED -> VOIDED. APPLIED and VOIDED are terminal. No self-transition.

### LineItemType (EXTEND existing)
- File: `src/main/kotlin/com/wakita181009/classic/model/LineItemType.kt`
- Add: `ADDON_CHARGE`, `ADDON_PRORATION_CREDIT`, `ADDON_PRORATION_CHARGE`, `SEAT_CHARGE`, `SEAT_PRORATION_CREDIT`, `SEAT_PRORATION_CHARGE`, `ACCOUNT_CREDIT`

---

## 2. JPA Entities

### AddOn (NEW)
- File: `src/main/kotlin/com/wakita181009/classic/model/AddOn.kt`
- `@Entity @Table(name = "addons") class AddOn`
- Fields: id(Long @Id @GeneratedValue), name(String), price(Money @Embedded with AttributeOverrides), billingType(BillingType @Enumerated STRING), compatibleTiers(Set<PlanTier> @ElementCollection @Enumerated STRING), active(Boolean), currency(Money.Currency - derived from price.currency)
- Init block: name.isNotBlank(), price.amount > 0, compatibleTiers.isNotEmpty()
- Note: currency is derived from price, not a separate column

### SubscriptionAddOn (NEW)
- File: `src/main/kotlin/com/wakita181009/classic/model/SubscriptionAddOn.kt`
- `@Entity @Table(name = "subscription_addons") class SubscriptionAddOn`
- Fields: id(Long @Id @GeneratedValue), subscription(@ManyToOne LAZY), addOn(@ManyToOne LAZY), quantity(Int), status(SubscriptionAddOnStatus @Enumerated STRING), attachedAt(Instant), detachedAt(Instant?)
- `transitionTo(newStatus)` method using canTransitionTo

### CreditNote (NEW)
- File: `src/main/kotlin/com/wakita181009/classic/model/CreditNote.kt`
- `@Entity @Table(name = "credit_notes") class CreditNote`
- Fields: id(Long @Id @GeneratedValue), invoice(@ManyToOne LAZY), subscription(@ManyToOne LAZY), amount(Money @Embedded with AttributeOverrides), reason(String), type(CreditNoteType @Enumerated STRING), application(CreditApplication @Enumerated STRING), status(CreditNoteStatus @Enumerated STRING), refundTransactionId(String?), createdAt(Instant), updatedAt(Instant)
- `transitionTo(newStatus)` method using canTransitionTo
- Init block: amount.amount > 0, reason.isNotBlank()

### Plan (EXTEND existing)
- File: `src/main/kotlin/com/wakita181009/classic/model/Plan.kt`
- Add fields: `perSeatPricing: Boolean = false`, `minimumSeats: Int = 1`, `maximumSeats: Int? = null`
- Add init block rules:
  - FREE cannot have perSeatPricing
  - if perSeatPricing: minimumSeats >= 1
  - if perSeatPricing and maximumSeats != null: maximumSeats >= minimumSeats

### Subscription (EXTEND existing)
- File: `src/main/kotlin/com/wakita181009/classic/model/Subscription.kt`
- Add fields:
  - `seatCount: Int? = null`
  - `accountCreditBalance: Money` (@Embedded with AttributeOverrides for account_credit_balance_amount/currency)
  - `@OneToMany(mappedBy = "subscription") subscriptionAddOns: MutableList<SubscriptionAddOn> = mutableListOf()`

---

## 3. Repositories

### AddOnRepository (NEW)
- File: `src/main/kotlin/com/wakita181009/classic/repository/AddOnRepository.kt`
- `JpaRepository<AddOn, Long>`
- `findByIdAndActiveTrue(id: Long): AddOn?`

### SubscriptionAddOnRepository (NEW)
- File: `src/main/kotlin/com/wakita181009/classic/repository/SubscriptionAddOnRepository.kt`
- `JpaRepository<SubscriptionAddOn, Long>`
- `findBySubscriptionIdAndAddOnIdAndStatus(subscriptionId: Long, addOnId: Long, status: SubscriptionAddOnStatus): SubscriptionAddOn?`
- `findBySubscriptionIdAndStatus(subscriptionId: Long, status: SubscriptionAddOnStatus): List<SubscriptionAddOn>`
- `findBySubscriptionId(subscriptionId: Long): List<SubscriptionAddOn>`
- `countBySubscriptionIdAndStatus(subscriptionId: Long, status: SubscriptionAddOnStatus): Long`

### CreditNoteRepository (NEW)
- File: `src/main/kotlin/com/wakita181009/classic/repository/CreditNoteRepository.kt`
- `JpaRepository<CreditNote, Long>`
- `findByInvoiceId(invoiceId: Long): List<CreditNote>`
- `findByInvoiceIdAndStatusIn(invoiceId: Long, statuses: List<CreditNoteStatus>): List<CreditNote>`

---

## 4. Exceptions (EXTEND existing file)

File: `src/main/kotlin/com/wakita181009/classic/exception/Exceptions.kt`

Add these new exceptions:
- `AddOnNotFoundException(id: Long)` -- 404
- `CurrencyMismatchException(expected: String, actual: String)` -- 409
- `TierIncompatibilityException(tier: String, compatibleTiers: String)` -- 409
- `DuplicateAddOnException(addonId: Long)` -- 409
- `AddOnLimitReachedException(limit: Int)` -- 409
- `NotPerSeatPlanException()` -- 409
- `SeatCountOutOfRangeException(message: String)` -- 409
- `SameSeatCountException(current: Int)` -- 409
- `SeatCountRequiredException()` -- 400
- `InvoiceNotPaidException(invoiceId: Long)` -- 409
- `AlreadyFullyRefundedException(invoiceId: Long)` -- 409
- `CreditAmountExceedsRemainingException(remaining: String, requested: String)` -- 409
- `PerSeatAddOnOnNonPerSeatPlanException()` -- 409
- `SubscriptionAddOnNotFoundException(subscriptionId: Long, addOnId: Long)` -- 404

---

## 5. Services

### AddOnService (NEW)
- File: `src/main/kotlin/com/wakita181009/classic/service/AddOnService.kt`
- `@Service` with constructor injection
- Dependencies: SubscriptionRepository, AddOnRepository, SubscriptionAddOnRepository, InvoiceRepository, PaymentGateway, Clock
- Methods:
  - `@Transactional attachAddOn(subscriptionId: Long, addOnId: Long): SubscriptionAddOn` -- UC-9
  - `@Transactional detachAddOn(subscriptionId: Long, addOnId: Long): SubscriptionAddOn` -- UC-10
  - `@Transactional(readOnly = true) listAddOns(subscriptionId: Long): List<SubscriptionAddOn>` -- list

### SeatService (NEW)
- File: `src/main/kotlin/com/wakita181009/classic/service/SeatService.kt`
- `@Service` with constructor injection
- Dependencies: SubscriptionRepository, SubscriptionAddOnRepository, InvoiceRepository, PaymentGateway, Clock
- Methods:
  - `@Transactional updateSeatCount(subscriptionId: Long, newSeatCount: Int): Subscription` -- UC-11

### CreditNoteService (NEW)
- File: `src/main/kotlin/com/wakita181009/classic/service/CreditNoteService.kt`
- `@Service` with constructor injection
- Dependencies: InvoiceRepository, CreditNoteRepository, SubscriptionRepository, PaymentGateway, Clock
- Methods:
  - `@Transactional issueCreditNote(invoiceId: Long, type: CreditNoteType, application: CreditApplication, amount: BigDecimal?, reason: String): CreditNote` -- UC-12
  - `@Transactional(readOnly = true) listCreditNotes(invoiceId: Long): List<CreditNote>` -- list

### SubscriptionService (EXTEND existing)
- Modify `createSubscription()`: handle seatCount, validate per-seat requirements, init accountCreditBalance to Money.zero
- Modify `changePlan()`: auto-detach incompatible add-ons, handle per-seat<->non-per-seat transitions
- Modify `processRenewal()`: include add-on charges, per-seat pricing (base_price * seat_count), account credit application after discount

### PaymentGateway (EXTEND existing)
- Modify `RefundResult`: add `refundTransactionId: String? = null` field

---

## 6. DTOs

### Request DTOs (EXTEND Requests.kt)
- `AttachAddOnRequest(@field:Positive addonId: Long)`
- `UpdateSeatCountRequest(@field:Positive seatCount: Int)`
- `IssueCreditNoteRequest(type: CreditNoteType, application: CreditApplication, amount: BigDecimal? = null, @field:NotBlank reason: String)`
- Extend `CreateSubscriptionRequest`: add `seatCount: Int? = null`

### Response DTOs (EXTEND Responses.kt)
- `AddOnResponse(id, name, price: MoneyResponse, billingType: String)` with `companion object { fun from(addOn: AddOn) }`
- `SubscriptionAddOnResponse(id, subscriptionId, addon: AddOnResponse, quantity, status, attachedAt, detachedAt)` with `companion object { fun from(sa: SubscriptionAddOn) }`
- `CreditNoteResponse(id, invoiceId, subscriptionId, amount: MoneyResponse, reason, type, application, status, refundTransactionId, createdAt, updatedAt)` with `companion object { fun from(cn: CreditNote) }`
- Extend `SubscriptionResponse`: add `seatCount: Int?`, `accountCreditBalance: MoneyResponse?`, `addons: List<SubscriptionAddOnResponse>`

---

## 7. Controllers

### SubscriptionController (EXTEND existing)
- Inject AddOnService, SeatService
- Add: `POST /{id}/addons` -> 201 (AttachAddOnRequest -> SubscriptionAddOnResponse)
- Add: `DELETE /{id}/addons/{addonId}` -> 200 (SubscriptionAddOnResponse)
- Add: `PUT /{id}/seats` -> 200 (UpdateSeatCountRequest -> SubscriptionResponse)
- Add: `GET /{id}/addons` -> 200 (List<SubscriptionAddOnResponse>)

### InvoiceController (EXTEND existing)
- Inject CreditNoteService
- Add: `POST /{id}/credit-notes` -> 201 (IssueCreditNoteRequest -> CreditNoteResponse)
- Add: `GET /{id}/credit-notes` -> 200 (List<CreditNoteResponse>)

---

## 8. Test Files

### New test files to create:
1. `src/test/kotlin/com/wakita181009/classic/model/AddOnTest.kt` -- 8 tests (AO-V1..V8)
2. `src/test/kotlin/com/wakita181009/classic/model/CreditNoteEntityTest.kt` -- 6 tests (CN-V1..V6)
3. `src/test/kotlin/com/wakita181009/classic/model/SubscriptionAddOnStatusTest.kt` -- 4 tests (SA-V1..V3, SA-I1)
4. `src/test/kotlin/com/wakita181009/classic/model/CreditNoteStatusTest.kt` -- 6 tests (CN-S1..S3, CN-I1..I3)
5. `src/test/kotlin/com/wakita181009/classic/model/PlanExtendedTest.kt` -- 6 tests (PL-V1..V6)
6. `src/test/kotlin/com/wakita181009/classic/model/SubscriptionSeatTest.kt` -- 6 tests (SC-V1..V6)
7. `src/test/kotlin/com/wakita181009/classic/service/AddOnServiceTest.kt` -- 22 tests (AO-H1..H6, AO-P1..P4, AO-E1..E12)
8. `src/test/kotlin/com/wakita181009/classic/service/SeatServiceTest.kt` -- 20 tests (ST-U1..U4, ST-D1..D3, ST-P1..P3, ST-E1..E10, SA-1..SA-6)
9. `src/test/kotlin/com/wakita181009/classic/service/CreditNoteServiceTest.kt` -- 18 tests (CN-H1..H7, CN-E1..E11)

### Existing test files to extend:
10. `src/test/kotlin/com/wakita181009/classic/service/SubscriptionServiceTest.kt` -- 26 tests (AR-1..8, CR-1..6, PC-1..6, CS-S1..S6)
11. `src/test/kotlin/com/wakita181009/classic/controller/SubscriptionControllerTest.kt` -- 15 tests (API-AO1..4, API-DA1..2, API-ST1..6, API-LA1..3)
12. `src/test/kotlin/com/wakita181009/classic/controller/InvoiceControllerTest.kt` -- 8 tests (API-CN1..5, API-LC1..3)

### Total new Phase 1 tests: ~156
