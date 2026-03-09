# Feature Spec: Subscription Management API

## 1. Context

A SaaS subscription management system that handles the full subscription lifecycle: plan selection with optional trial periods, recurring billing with proration on plan changes, usage-based metering, pause/resume functionality, payment failure recovery with grace periods, and cancellation with end-of-period or immediate options.

This system serves as the backend API for a SaaS platform's billing and subscription management.

---

## 2. Domain Model

### 2.1 Plan

A billing plan that customers can subscribe to. Plans are predefined and immutable during a subscription period.

**Fields:**
- Plan ID: unique identifier, positive integer
- Name: non-blank string (e.g., "Starter", "Professional", "Enterprise")
- Billing interval: MONTHLY or YEARLY
- Base price: a monetary amount, must be greater than zero (exception: free-tier plans have zero price)
- Usage limit: optional, maximum units of metered usage per billing period (null means unlimited)
- Features: a set of feature flags (non-empty set of strings)
- Tier: FREE, STARTER, PROFESSIONAL, ENTERPRISE (ordered by rank for upgrade/downgrade determination)
- Active: boolean, only active plans can be subscribed to

**Business rules:**
- FREE tier plans must have base price of zero.
- Non-FREE tier plans must have base price greater than zero.
- YEARLY plans receive a 20% discount over monthly equivalent (enforced at plan creation, not runtime).
- Plan tier determines upgrade/downgrade: moving to a higher tier is an upgrade, lower tier is a downgrade.

### 2.2 Subscription

The central entity. Represents a customer's ongoing relationship with a plan.

**Fields:**
- Subscription ID: unique identifier, positive integer, auto-generated
- Customer ID: positive integer, required
- Plan: the currently active plan
- Status: the current lifecycle state (see §3)
- Current period start: the start of the current billing period
- Current period end: the end of the current billing period
- Trial start: timestamp when trial began (null if no trial)
- Trial end: timestamp when trial ends (null if no trial)
- Paused at: timestamp when subscription was paused (null if not paused)
- Canceled at: timestamp when cancellation was requested (null if not canceled)
- Cancel at period end: boolean, whether cancellation takes effect at period end
- Grace period end: timestamp, deadline for payment recovery (null if not in grace period)
- Created timestamp: set automatically when subscription is created
- Updated timestamp: set automatically on every modification

**Business rules:**
- A customer may have at most ONE active subscription (status in Trial, Active, Paused, PastDue) at any time.
- Trial period is always 14 days from creation.
- Grace period for failed payments is 7 days.
- Paused subscriptions do not generate invoices and billing period is frozen.
- A subscription can be paused at most 2 times within a single billing period.
- Maximum consecutive pause duration is 30 days. After 30 days paused, auto-cancel.

### 2.3 Money

Represents a monetary value with currency. Same rules as order system.

**Fields:**
- Amount: a decimal number, with 2 decimal places (exception: JPY uses 0 decimal places)
- Currency: one of USD, EUR, or JPY

**Business rules:**
- Arithmetic operations are only valid between the same currency.
- JPY amounts must have zero decimal places.
- Negative Money is allowed for credits and proration adjustments.
- Proration calculations must round to the currency's scale (HALF_UP rounding).

### 2.4 Invoice

A billing document generated for each billing period or plan change.

**Fields:**
- Invoice ID: unique identifier, positive integer, auto-generated
- Subscription ID: reference to the subscription
- Line items: list of invoice line items (at least 1)
- Subtotal: sum of all line items before discount
- Discount amount: the discount applied (zero if none)
- Total: subtotal minus discount amount, must be zero or greater
- Currency: must match the subscription's plan currency
- Status: Draft, Open, Paid, Void, Uncollectible
- Due date: the date payment is expected
- Paid at: timestamp when payment was collected (null until paid)
- Payment attempt count: number of charge attempts (starts at 0)
- Created timestamp
- Updated timestamp

**Business rules:**
- An invoice with total of zero is auto-marked as Paid (no charge needed).
- Maximum payment attempts before marking Uncollectible: 3.
- Due date is the first day of the billing period.

### 2.5 Invoice Line Item

A single charge or credit on an invoice.

**Fields:**
- Description: non-blank string explaining the charge
- Amount: monetary amount (may be negative for credits/proration)
- Type: one of PLAN_CHARGE, USAGE_CHARGE, PRORATION_CREDIT, PRORATION_CHARGE

### 2.6 Usage Record

Tracks metered usage for usage-based billing components.

**Fields:**
- Usage ID: unique identifier, positive integer, auto-generated
- Subscription ID: reference to the subscription
- Metric name: non-blank string identifying what is being measured (e.g., "api_calls", "storage_gb")
- Quantity: positive integer, must be at least 1
- Recorded at: timestamp when usage occurred
- Idempotency key: unique string per usage record, prevents duplicate recording

**Business rules:**
- Usage can only be recorded for Active subscriptions.
- Usage quantity for a billing period must not exceed the plan's usage limit (if set).
- Usage recorded outside the current billing period is rejected.

### 2.7 Discount

A discount applied to a subscription.

**Fields:**
- Discount ID: unique identifier, positive integer, auto-generated
- Type: PERCENTAGE or FIXED_AMOUNT
- Value: for PERCENTAGE, a number between 1 and 100 inclusive; for FIXED_AMOUNT, a positive monetary amount
- Duration months: how many billing cycles the discount applies (1 to 24, or null for forever)
- Remaining cycles: how many more billing cycles the discount will be applied
- Applied at: timestamp when discount was attached to subscription

**Business rules:**
- A subscription may have at most ONE active discount at a time.
- PERCENTAGE discount: `discount = subtotal * (value / 100)`, rounded to currency scale.
- FIXED_AMOUNT discount: `discount = min(value, subtotal)` — discount cannot exceed subtotal.
- Discount is consumed each billing cycle (remaining cycles decremented). When remaining cycles reaches 0, discount expires.
- Discount does NOT apply to proration invoices from plan changes.

---

## 3. State Machines

### 3.1 Subscription Status

**States:** Trial, Active, Paused, PastDue, Canceled, Expired

**Allowed transitions:**

- Trial to Active: triggered when trial period ends and first payment succeeds
- Trial to Canceled: triggered by customer cancellation during trial (immediate, no charge)
- Trial to Expired: triggered when trial ends and payment fails (auto-expire after grace period)
- Active to Paused: triggered by customer request. Billing period is frozen.
- Active to PastDue: triggered when renewal payment fails. Grace period begins.
- Active to Canceled: triggered by customer cancellation (immediate or end-of-period)
- Paused to Active: triggered by customer resume. Billing period resumes from where it was frozen.
- Paused to Canceled: triggered by customer cancellation while paused (immediate)
- Paused to Canceled: triggered by auto-cancel after 30 days paused
- PastDue to Active: triggered when overdue payment is recovered within grace period
- PastDue to Canceled: triggered when grace period expires without payment recovery

**Terminal states:** Canceled, Expired

**All transitions not listed above are invalid.** Attempting an invalid transition is an error.

### 3.2 Invoice Status

**States:** Draft, Open, Paid, Void, Uncollectible

**Allowed transitions:**

- Draft to Open: invoice is finalized and ready for payment
- Draft to Void: invoice is discarded before finalization
- Open to Paid: payment succeeds
- Open to Void: invoice is voided (e.g., subscription canceled before payment)
- Open to Uncollectible: all payment attempts exhausted (3 attempts)

**Terminal states:** Paid, Void, Uncollectible

---

## 4. Use Cases

### UC-1: Create Subscription

**Actor:** Customer (via API)

**Input:** customer ID, plan ID, payment method, discount code (optional)

**Happy path:**
1. Validate all input fields. Customer ID must be positive. Plan ID must reference an active plan.
2. Verify the customer does not already have an active subscription (status in Trial, Active, Paused, PastDue).
3. If discount code is provided, validate and resolve it.
4. Create the subscription with status Trial, trial end = now + 14 days.
5. Set current period start = now, current period end = now + 14 days (aligned to trial).
6. No charge during trial — first invoice is generated when trial ends.

**Error cases:**
- Invalid customer ID: respond with 400
- Plan not found or inactive: respond with 404
- Customer already has active subscription: respond with 409
- Invalid discount code: respond with 400
- Internal error: respond with 500

### UC-2: Change Plan

**Actor:** Customer (via API)

**Input:** subscription ID, new plan ID

**Happy path:**
1. Validate subscription ID and new plan ID.
2. Find the subscription. Must be in Active status.
3. Verify the new plan is different from the current plan.
4. Verify the new plan is active.
5. Verify currency matches (cannot change currency mid-subscription).
6. Calculate proration:
   a. Determine days remaining in current period.
   b. Calculate credit for unused portion of current plan: `current_plan_price * (days_remaining / total_days_in_period)`.
   c. Calculate charge for new plan for remaining days: `new_plan_price * (days_remaining / total_days_in_period)`.
   d. Net proration = charge - credit. May be negative (downgrade) or positive (upgrade).
7. Generate a proration invoice with line items: PRORATION_CREDIT for old plan, PRORATION_CHARGE for new plan.
8. If net proration is positive (upgrade): charge immediately via payment gateway.
9. If net proration is negative or zero (downgrade): apply as credit, no immediate charge. Credit is applied to next renewal invoice.
10. Update subscription's plan.
11. If immediate charge fails: do NOT change plan, respond with 502.

**Error cases:**
- Invalid subscription ID: respond with 400
- Subscription not found: respond with 404
- Subscription not in Active status: respond with 409
- New plan same as current plan: respond with 409
- New plan not found or inactive: respond with 404
- Currency mismatch between plans: respond with 409
- Payment gateway failure (on upgrade): respond with 502. Plan NOT changed.
- Internal error: respond with 500

**Critical requirement:** Proration calculation must be precise to the day. Both the credit and charge amounts must use `HALF_UP` rounding at the currency's scale.

### UC-3: Pause Subscription

**Actor:** Customer (via API)

**Input:** subscription ID

**Happy path:**
1. Validate subscription ID.
2. Find the subscription. Must be in Active status.
3. Verify the subscription has not been paused 2 times already in the current billing period.
4. Set status to Paused, record paused_at timestamp.
5. Billing period is frozen (the remaining days are preserved, not consumed).

**Error cases:**
- Invalid subscription ID: respond with 400
- Subscription not found: respond with 404
- Subscription not in Active status: respond with 409
- Pause limit reached (2 pauses in current period): respond with 409
- Internal error: respond with 500

### UC-4: Resume Subscription

**Actor:** Customer (via API)

**Input:** subscription ID

**Happy path:**
1. Validate subscription ID.
2. Find the subscription. Must be in Paused status.
3. Calculate the remaining days that were frozen when paused.
4. Set current period end = now + remaining days.
5. Set status to Active, clear paused_at.

**Error cases:**
- Invalid subscription ID: respond with 400
- Subscription not found: respond with 404
- Subscription not in Paused status: respond with 409
- Internal error: respond with 500

### UC-5: Cancel Subscription

**Actor:** Customer (via API)

**Input:** subscription ID, immediate (boolean, default false)

**Happy path (immediate=false, end-of-period cancellation):**
1. Validate subscription ID.
2. Find the subscription. Must be in Active or PastDue status.
3. Set cancel_at_period_end = true, canceled_at = now.
4. Subscription remains Active until period end, then transitions to Canceled.

**Happy path (immediate=true):**
1. Validate subscription ID.
2. Find the subscription. Must be in Active, Paused, or PastDue status.
3. Transition to Canceled immediately.
4. Void any open invoices for this subscription.
5. If in Trial status: immediate cancellation, no charge.

**Error cases:**
- Invalid subscription ID: respond with 400
- Subscription not found: respond with 404
- Subscription already canceled or expired: respond with 409
- Internal error: respond with 500

### UC-6: Process Renewal

**Actor:** System (scheduled job)

**Input:** subscription ID

**Happy path:**
1. Find the subscription. Must be in Active status with current period end <= now.
2. Generate a renewal invoice:
   a. Add PLAN_CHARGE line item for the plan's base price.
   b. If usage-based billing applies, add USAGE_CHARGE line items for metered usage during the ended period.
   c. Apply discount if active (decrement remaining cycles).
3. Charge via payment gateway.
4. If payment succeeds: mark invoice as Paid, advance billing period (start = old end, end = old end + interval).
5. If payment fails: mark invoice as Open, transition subscription to PastDue, start grace period (7 days).

**Error cases:**
- Subscription not in Active status: skip (no-op)
- Subscription period has not ended: skip (no-op)
- Payment failure: transition to PastDue (not an HTTP error — this is a background job)
- Internal error: log and retry

**Critical requirement:** Usage charges must be calculated from usage records within the billing period boundaries. Usage records outside the period must be excluded.

### UC-7: Record Usage

**Actor:** Customer application (via API)

**Input:** subscription ID, metric name, quantity, idempotency key

**Happy path:**
1. Validate all input fields.
2. Find the subscription. Must be in Active status.
3. Check idempotency: if a record with this key already exists, return the existing record (idempotent).
4. Verify the usage does not exceed the plan's usage limit for the current period.
5. Create and save the usage record.

**Error cases:**
- Invalid subscription ID: respond with 400
- Subscription not found: respond with 404
- Subscription not in Active status: respond with 409
- Usage limit exceeded: respond with 409
- Invalid quantity (less than 1): respond with 400
- Blank metric name: respond with 400
- Blank idempotency key: respond with 400
- Internal error: respond with 500

### UC-8: Recover Payment

**Actor:** System (retry job) or Customer (manual retry via API)

**Input:** invoice ID

**Happy path:**
1. Find the invoice. Must be in Open status.
2. Find the associated subscription. Must be in PastDue status.
3. Verify grace period has not expired.
4. Attempt payment via gateway.
5. If payment succeeds: mark invoice as Paid, transition subscription to Active, clear grace period.
6. If payment fails: increment attempt count. If attempts >= 3, mark invoice as Uncollectible, transition subscription to Canceled.

**Error cases:**
- Invalid invoice ID: respond with 400
- Invoice not found: respond with 404
- Invoice not in Open status: respond with 409
- Grace period expired: respond with 409. Transition subscription to Canceled.
- Payment gateway failure: increment attempt count, respond with 502.
- Internal error: respond with 500

**Critical requirement:** When grace period expires, subscription MUST be canceled and invoice marked Uncollectible — even if this is discovered during a retry attempt.

---

## 5. API Endpoints

### REST API

| Method | Path | Description | Success | Error codes |
|--------|------|-------------|---------|-------------|
| POST | /api/subscriptions | Create subscription | 201 Created | 400, 404, 409, 500 |
| PUT | /api/subscriptions/{id}/plan | Change plan | 200 OK | 400, 404, 409, 500, 502 |
| POST | /api/subscriptions/{id}/pause | Pause subscription | 200 OK | 400, 404, 409, 500 |
| POST | /api/subscriptions/{id}/resume | Resume subscription | 200 OK | 400, 404, 409, 500 |
| POST | /api/subscriptions/{id}/cancel | Cancel subscription | 200 OK | 400, 404, 409, 500 |
| POST | /api/subscriptions/{id}/usage | Record usage | 201 Created | 400, 404, 409, 500 |
| POST | /api/invoices/{id}/pay | Recover payment | 200 OK | 400, 404, 409, 500, 502 |
| GET | /api/subscriptions/{id} | Get subscription | 200 OK | 400, 404, 500 |
| GET | /api/subscriptions?customerId={id} | List by customer | 200 OK | 400, 500 |
| GET | /api/invoices?subscriptionId={id} | List invoices | 200 OK | 400, 500 |

### Data Flow: Command vs Query

**Command endpoints** (POST/PUT):
- Use use cases that operate on domain entities
- Flow: Controller → UseCase → Domain Entity → Domain Repository

**Query endpoints** (GET):
- Use query repositories that return DTOs directly
- Flow: Controller → QueryUseCase → QueryRepository → Infrastructure (jOOQ → DTO)

### Request: Create Subscription

```json
{
  "customerId": 1,
  "planId": 2,
  "paymentMethod": "CREDIT_CARD",
  "discountCode": "WELCOME20"
}
```

### Request: Change Plan

```json
{
  "newPlanId": 3
}
```

### Request: Cancel Subscription

```json
{
  "immediate": false
}
```

### Request: Record Usage

```json
{
  "metricName": "api_calls",
  "quantity": 150,
  "idempotencyKey": "req-abc-123"
}
```

### Response: Subscription

```json
{
  "id": 1,
  "customerId": 1,
  "plan": {
    "id": 2,
    "name": "Professional",
    "tier": "PROFESSIONAL",
    "billingInterval": "MONTHLY",
    "basePrice": { "amount": 49.99, "currency": "USD" }
  },
  "status": "ACTIVE",
  "currentPeriodStart": "2025-01-01T00:00:00Z",
  "currentPeriodEnd": "2025-02-01T00:00:00Z",
  "trialEnd": null,
  "pausedAt": null,
  "canceledAt": null,
  "cancelAtPeriodEnd": false,
  "discount": {
    "type": "PERCENTAGE",
    "value": 20,
    "remainingCycles": 3
  },
  "createdAt": "2024-12-15T10:00:00Z",
  "updatedAt": "2025-01-01T00:00:00Z"
}
```

### Response: Invoice

```json
{
  "id": 1,
  "subscriptionId": 1,
  "lineItems": [
    { "description": "Professional Plan - Monthly", "amount": { "amount": 49.99, "currency": "USD" }, "type": "PLAN_CHARGE" },
    { "description": "API Calls: 1500 units", "amount": { "amount": 15.00, "currency": "USD" }, "type": "USAGE_CHARGE" }
  ],
  "subtotal": { "amount": 64.99, "currency": "USD" },
  "discountAmount": { "amount": 13.00, "currency": "USD" },
  "total": { "amount": 51.99, "currency": "USD" },
  "status": "PAID",
  "dueDate": "2025-01-01",
  "paidAt": "2025-01-01T00:05:00Z",
  "paymentAttemptCount": 1,
  "createdAt": "2025-01-01T00:00:00Z"
}
```

---

## 6. External Dependencies

### Payment Gateway

Same interface as order system but with additional operations:

**Charge operation:**
- Input: monetary amount, payment method, customer reference
- Output on success: transaction ID (string) and processed timestamp
- Output on failure: error reason (declined, timeout, insufficient_funds, etc.)

**Refund operation:**
- Input: original transaction ID, amount to refund
- Output on success: confirmation
- Output on failure: error reason

### Database

PostgreSQL is the backing database. The schema must support all entities described in §2.

---

## 7. Business Rules Summary

1. A customer may have at most one active subscription at any time.
2. Trial period is exactly 14 days from subscription creation.
3. Grace period for failed payments is 7 days.
4. A subscription can be paused at most 2 times per billing period.
5. Maximum consecutive pause duration is 30 days; auto-cancel after that.
6. Plan changes require proration calculated to the day with HALF_UP rounding.
7. Upgrade (higher tier) charges immediately; downgrade (lower tier) credits to next invoice.
8. Currency cannot change during a plan change.
9. Usage cannot exceed plan limits within a billing period.
10. Usage can only be recorded for Active subscriptions.
11. Invoices with zero total are auto-marked as Paid.
12. Maximum 3 payment attempts before invoice is Uncollectible.
13. Discount does not apply to proration invoices.
14. A subscription may have at most one active discount.
15. PERCENTAGE discount value must be between 1 and 100 inclusive.
16. FREE tier plans must have base price of zero.
17. YEARLY billing interval applies 20% discount over monthly equivalent.
18. Idempotency keys prevent duplicate usage recording.
19. Subscription state transitions follow §3.1 strictly.
20. Invoice state transitions follow §3.2 strictly.
