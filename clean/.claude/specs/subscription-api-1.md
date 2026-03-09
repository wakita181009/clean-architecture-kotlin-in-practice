# Feature Spec: Subscription Management API — Phase 1

Phase 1 extends the Phase 0 subscription system (subscription-api.md) with three feature areas: **Add-on Management**, **Seat Management**, and **Credit Notes (Refunds)**. All Phase 0 domain models, state machines, and business rules remain in effect. This document defines only the additions and extensions.

---

## 1. New Domain Models

### 1.1 AddOn

A purchasable optional feature that can be attached to a subscription. AddOns are predefined products, similar to Plans.

**Fields:**
- AddOn ID: unique identifier, positive integer
- Name: non-blank string (e.g., "Priority Support", "Advanced Analytics", "Extra Storage 100GB")
- Price: a monetary amount per billing period, must be greater than zero
- Billing type: FLAT or PER_SEAT
  - FLAT: fixed price regardless of seat count
  - PER_SEAT: price is multiplied by the subscription's seat count
- Compatible tiers: non-empty set of Plan tiers (FREE, STARTER, PROFESSIONAL, ENTERPRISE) this add-on is available for
- Active: boolean, only active add-ons can be attached
- Currency: one of USD, EUR, JPY (must match subscription's plan currency when attaching)

**Business rules:**
- AddOn price must be greater than zero.
- Compatible tiers must be a non-empty set.
- PER_SEAT add-ons can only be attached to subscriptions on per-seat plans.
- AddOn currency must match the subscription's plan currency.

### 1.2 SubscriptionAddOn

A link entity representing an add-on attached to a specific subscription.

**Fields:**
- SubscriptionAddOn ID: unique identifier, positive integer, auto-generated
- Subscription ID: reference to the subscription
- AddOn ID: reference to the add-on
- Quantity: positive integer. For FLAT billing type, always 1. For PER_SEAT, equals the subscription's seat count at time of attachment (and auto-updated on seat changes).
- Status: Active or Detached
- Attached at: timestamp when the add-on was attached
- Detached at: timestamp when the add-on was detached (null if Active)

**Business rules:**
- A subscription may have at most 5 active SubscriptionAddOns at any time.
- The same AddOn cannot be attached twice to the same subscription (no duplicate active attachments).
- When the subscription's plan changes, any SubscriptionAddOns whose add-on is not compatible with the new plan tier are automatically detached (with proration credit).
- When the subscription's seat count changes, all PER_SEAT SubscriptionAddOns have their quantity updated to match the new seat count.

### 1.3 CreditNote

A document representing a full or partial refund issued against a paid invoice.

**Fields:**
- CreditNote ID: unique identifier, positive integer, auto-generated
- Invoice ID: reference to the original paid invoice
- Subscription ID: reference to the subscription (derived from invoice)
- Amount: monetary amount, must be greater than zero
- Currency: must match invoice currency
- Reason: non-blank string explaining the refund
- Type: FULL or PARTIAL
- Application: REFUND_TO_PAYMENT or ACCOUNT_CREDIT
  - REFUND_TO_PAYMENT: refund is processed back through the payment gateway
  - ACCOUNT_CREDIT: credit is stored as account balance, applied to future invoices
- Status: Issued, Applied, Voided
- Refund transaction ID: string from payment gateway (null for ACCOUNT_CREDIT or until refund is processed)
- Created timestamp
- Updated timestamp

**Business rules:**
- Credit notes can only be issued for invoices with status Paid.
- The sum of all credit note amounts for a single invoice must not exceed the invoice's total.
- FULL type: amount equals the invoice total minus any previously issued credit notes for that invoice.
- PARTIAL type: amount is specified by the caller, must be > 0 and <= remaining refundable amount.
- REFUND_TO_PAYMENT: triggers a refund operation via the payment gateway. If the refund succeeds, status transitions to Applied. If it fails, status remains Issued and the operation returns 502.
- ACCOUNT_CREDIT: immediately transitions to Applied. The credit balance is recorded and applied automatically to the next renewal invoice.
- Account credit balance is applied before charging the payment gateway during renewal (UC-6).

---

## 2. Extended Domain Models

### 2.1 Plan (extended)

Phase 0 Plan is extended with seat-related fields:

**New fields:**
- Per-seat pricing: boolean (default false). When true, `base_price` represents the price per seat.
- Minimum seats: positive integer (default 1). Only relevant when per-seat pricing is true.
- Maximum seats: positive integer or null (null means unlimited). Only relevant when per-seat pricing is true.

**Business rules:**
- When per-seat pricing is true, minimum seats must be at least 1.
- When per-seat pricing is true, maximum seats (if set) must be >= minimum seats.
- When per-seat pricing is false, minimum seats and maximum seats are ignored.
- FREE tier plans cannot have per-seat pricing.

### 2.2 Subscription (extended)

Phase 0 Subscription is extended:

**New fields:**
- Seat count: positive integer or null. Null for plans without per-seat pricing. For per-seat plans, must be >= plan's minimum seats and <= plan's maximum seats (if set).
- Account credit balance: monetary amount (default zero). Accumulated from ACCOUNT_CREDIT credit notes. Applied during renewal.

**Business rules:**
- When subscribing to a per-seat plan (UC-1), seat count must be provided and must be >= plan's minimum seats.
- Account credit balance is reduced during renewal (UC-6). If the credit exceeds the invoice total, only the invoice total is consumed; the remainder stays as balance.
- Account credit balance currency must match the subscription's plan currency.

### 2.3 Invoice Line Item (extended)

Phase 0 Invoice Line Item types are extended:

**New types:**
- ADDON_CHARGE: charge for an add-on during a billing period
- ADDON_PRORATION_CREDIT: credit for unused portion of a detached add-on
- ADDON_PRORATION_CHARGE: charge for remaining portion when attaching an add-on mid-cycle
- SEAT_CHARGE: charge for seats during renewal (base_price * seat_count)
- SEAT_PRORATION_CREDIT: credit for reduced seats (old_count - new_count) * per_seat_price * (remaining_days / total_days)
- SEAT_PRORATION_CHARGE: charge for added seats (new_count - old_count) * per_seat_price * (remaining_days / total_days)
- ACCOUNT_CREDIT: negative line item representing account credit applied to an invoice

---

## 3. New State Machines

### 3.1 SubscriptionAddOn Status

**States:** Active, Detached

**Allowed transitions:**
- Active to Detached: triggered by customer request (UC-10), plan change incompatibility (UC-2), or subscription cancellation

**Terminal states:** Detached

### 3.2 CreditNote Status

**States:** Issued, Applied, Voided

**Allowed transitions:**
- Issued to Applied: refund processed successfully (REFUND_TO_PAYMENT) or immediately (ACCOUNT_CREDIT)
- Issued to Voided: credit note is voided before application (e.g., issued in error)

**Terminal states:** Applied, Voided

---

## 4. Use Cases

### UC-9: Attach Add-on

**Actor:** Customer (via API)

**Input:** subscription ID, add-on ID

**Happy path:**
1. Validate subscription ID and add-on ID.
2. Find the subscription. Must be in Active status.
3. Find the add-on. Must be active.
4. Verify currency match: add-on currency must equal subscription's plan currency.
5. Verify tier compatibility: subscription's plan tier must be in add-on's compatible tiers.
6. If add-on billing type is PER_SEAT, verify the subscription is on a per-seat plan.
7. Verify the subscription does not already have this add-on attached (Active status).
8. Verify the subscription has fewer than 5 active add-ons.
9. Calculate proration for remaining days in current period:
   a. Determine days remaining in current billing period.
   b. For FLAT: `prorated_charge = addon_price * (days_remaining / total_days_in_period)`.
   c. For PER_SEAT: `prorated_charge = addon_price * seat_count * (days_remaining / total_days_in_period)`.
   d. Apply HALF_UP rounding at currency scale.
10. Generate a proration invoice with line item type ADDON_PRORATION_CHARGE.
11. Charge immediately via payment gateway.
12. If payment succeeds: create SubscriptionAddOn with status Active, quantity = 1 (FLAT) or seat_count (PER_SEAT).
13. If payment fails: do NOT attach the add-on, respond with 502.

**Error cases:**
- Invalid subscription ID: respond with 400
- Subscription not found: respond with 404
- Subscription not in Active status: respond with 409
- Add-on not found or inactive: respond with 404
- Currency mismatch: respond with 409
- Tier incompatibility: respond with 409
- PER_SEAT add-on on non-per-seat plan: respond with 409
- Duplicate add-on: respond with 409
- Add-on limit reached (5): respond with 409
- Payment gateway failure: respond with 502. Add-on NOT attached.
- Internal error: respond with 500

### UC-10: Detach Add-on

**Actor:** Customer (via API)

**Input:** subscription ID, add-on ID

**Happy path:**
1. Validate subscription ID and add-on ID.
2. Find the subscription. Must be in Active or Paused status.
3. Find the SubscriptionAddOn for this subscription and add-on. Must be in Active status.
4. Calculate proration credit for unused days:
   a. Determine days remaining in current billing period (for Paused subscriptions, use the frozen remaining days).
   b. For FLAT: `proration_credit = addon_price * (days_remaining / total_days_in_period)`.
   c. For PER_SEAT: `proration_credit = addon_price * quantity * (days_remaining / total_days_in_period)`.
   d. Apply HALF_UP rounding at currency scale.
5. Generate a proration invoice with line item type ADDON_PRORATION_CREDIT (negative amount).
6. If credit amount > 0: add to subscription's account credit balance.
7. Transition SubscriptionAddOn to Detached, set detached_at = now.

**Error cases:**
- Invalid subscription ID or add-on ID: respond with 400
- Subscription not found: respond with 404
- Subscription not in Active or Paused status: respond with 409
- Add-on not attached or already detached: respond with 404
- Internal error: respond with 500

### UC-11: Update Seat Count

**Actor:** Customer (via API)

**Input:** subscription ID, new seat count

**Happy path:**
1. Validate subscription ID and new seat count.
2. Find the subscription. Must be in Active status.
3. Verify the subscription is on a per-seat plan.
4. Verify new seat count is different from current seat count.
5. Verify new seat count >= plan's minimum seats.
6. Verify new seat count <= plan's maximum seats (if set).
7. Calculate proration:
   a. Determine days remaining in current billing period.
   b. `seat_difference = new_seat_count - old_seat_count`
   c. `proration_amount = plan_base_price * abs(seat_difference) * (days_remaining / total_days_in_period)`
   d. Apply HALF_UP rounding at currency scale.
8. If seat increase (seat_difference > 0):
   a. Generate proration invoice with SEAT_PRORATION_CHARGE.
   b. Charge immediately via payment gateway.
   c. If payment fails: do NOT change seat count, respond with 502.
9. If seat decrease (seat_difference < 0):
   a. Generate proration invoice with SEAT_PRORATION_CREDIT (negative amount).
   b. Add credit to subscription's account credit balance. No immediate charge.
10. Update subscription's seat count.
11. Update all PER_SEAT SubscriptionAddOns: set quantity = new_seat_count.
12. For PER_SEAT add-on proration on seat increase:
    a. For each active PER_SEAT add-on: `addon_proration = addon_price * seat_difference * (days_remaining / total_days_in_period)`.
    b. Add ADDON_PRORATION_CHARGE line items to the same invoice.
13. For PER_SEAT add-on proration on seat decrease:
    a. For each active PER_SEAT add-on: `addon_credit = addon_price * abs(seat_difference) * (days_remaining / total_days_in_period)`.
    b. Add ADDON_PRORATION_CREDIT line items, add to account credit balance.

**Error cases:**
- Invalid subscription ID: respond with 400
- Invalid seat count (zero or negative): respond with 400
- Subscription not found: respond with 404
- Subscription not in Active status: respond with 409
- Not a per-seat plan: respond with 409
- Same seat count: respond with 409
- Below minimum seats: respond with 409
- Above maximum seats: respond with 409
- Payment gateway failure (on seat increase): respond with 502. Seat count NOT changed.
- Internal error: respond with 500

### UC-12: Issue Credit Note

**Actor:** Admin (via API)

**Input:** invoice ID, type (FULL or PARTIAL), application (REFUND_TO_PAYMENT or ACCOUNT_CREDIT), amount (required for PARTIAL, ignored for FULL), reason

**Happy path:**
1. Validate all input fields.
2. Find the invoice. Must be in Paid status.
3. Calculate remaining refundable amount: `invoice_total - sum(existing_credit_notes_for_this_invoice)`.
4. If type is FULL: set amount = remaining refundable amount. If remaining is zero, respond with 409 (already fully refunded).
5. If type is PARTIAL: verify amount > 0 and amount <= remaining refundable amount.
6. Create CreditNote with status Issued.
7. If application is REFUND_TO_PAYMENT:
   a. Call payment gateway refund operation with the original transaction ID and refund amount.
   b. If refund succeeds: set refund transaction ID, transition to Applied.
   c. If refund fails: credit note remains Issued, respond with 502.
8. If application is ACCOUNT_CREDIT:
   a. Add amount to subscription's account credit balance.
   b. Transition to Applied immediately.

**Error cases:**
- Invalid invoice ID: respond with 400
- Invoice not found: respond with 404
- Invoice not in Paid status: respond with 409
- Already fully refunded (remaining = 0): respond with 409
- Partial amount exceeds remaining: respond with 409
- Partial amount <= 0: respond with 400
- Blank reason: respond with 400
- Payment gateway refund failure: respond with 502. Credit note stays Issued (can retry).
- Internal error: respond with 500

---

## 5. Phase 0 Use Case Extensions

### UC-1: Create Subscription (extended)

**Additional input:** seat count (required if plan has per-seat pricing)

**Additional validation:**
- If plan has per-seat pricing and seat count is not provided: respond with 400.
- If plan has per-seat pricing: seat count must be >= plan's minimum seats and <= plan's maximum seats (if set).
- If plan does not have per-seat pricing and seat count is provided: ignore it (set to null).

**Additional happy path:**
- Set subscription's seat count from input (or null for non-per-seat plans).
- Initialize account credit balance to zero.

### UC-2: Change Plan (extended)

**Additional happy path (after step 10 — plan updated):**
11. Check all active SubscriptionAddOns for compatibility with the new plan's tier.
12. For each incompatible add-on:
    a. Calculate proration credit for unused days (same formula as UC-10).
    b. Transition SubscriptionAddOn to Detached.
    c. Add ADDON_PRORATION_CREDIT line item to the proration invoice.
13. If changing from per-seat to non-per-seat plan: set subscription's seat count to null, detach all PER_SEAT add-ons with credit.
14. If changing from non-per-seat to per-seat plan: set subscription's seat count to new plan's minimum seats.

### UC-6: Process Renewal (extended)

**Additional happy path (at step 2 — generating renewal invoice):**
- 2b. For per-seat plans: the PLAN_CHARGE line item amount = base_price * seat_count (not just base_price).
- 2c. For each active SubscriptionAddOn:
  - FLAT: add ADDON_CHARGE line item with add-on price.
  - PER_SEAT: add ADDON_CHARGE line item with add-on price * quantity.
- 2d. If subscription has account credit balance > 0:
  - Calculate credit to apply: `min(account_credit_balance, invoice_subtotal_after_discount)`.
  - Add ACCOUNT_CREDIT line item (negative amount).
  - Reduce subscription's account credit balance by the applied amount.
  - Recalculate invoice total.

---

## 6. API Endpoints (Phase 1)

### REST API

| Method | Path | Description | Success | Error codes |
|--------|------|-------------|---------|-------------|
| POST | /api/subscriptions/{id}/addons | Attach add-on | 201 Created | 400, 404, 409, 500, 502 |
| DELETE | /api/subscriptions/{id}/addons/{addonId} | Detach add-on | 200 OK | 400, 404, 409, 500 |
| PUT | /api/subscriptions/{id}/seats | Update seat count | 200 OK | 400, 404, 409, 500, 502 |
| POST | /api/invoices/{id}/credit-notes | Issue credit note | 201 Created | 400, 404, 409, 500, 502 |
| GET | /api/subscriptions/{id}/addons | List subscription add-ons | 200 OK | 400, 500 |
| GET | /api/invoices/{id}/credit-notes | List credit notes | 200 OK | 400, 500 |

### Request: Attach Add-on

```json
{
  "addonId": 1
}
```

### Request: Update Seat Count

```json
{
  "seatCount": 10
}
```

### Request: Issue Credit Note

```json
{
  "type": "PARTIAL",
  "application": "ACCOUNT_CREDIT",
  "amount": 25.00,
  "reason": "Service disruption compensation"
}
```

### Response: SubscriptionAddOn

```json
{
  "id": 1,
  "subscriptionId": 1,
  "addon": {
    "id": 1,
    "name": "Priority Support",
    "price": { "amount": 9.99, "currency": "USD" },
    "billingType": "FLAT"
  },
  "quantity": 1,
  "status": "ACTIVE",
  "attachedAt": "2025-01-15T10:00:00Z",
  "detachedAt": null
}
```

### Response: CreditNote

```json
{
  "id": 1,
  "invoiceId": 1,
  "subscriptionId": 1,
  "amount": { "amount": 25.00, "currency": "USD" },
  "reason": "Service disruption compensation",
  "type": "PARTIAL",
  "application": "ACCOUNT_CREDIT",
  "status": "APPLIED",
  "refundTransactionId": null,
  "createdAt": "2025-02-01T10:00:00Z",
  "updatedAt": "2025-02-01T10:00:00Z"
}
```

### Response: Subscription (extended)

Phase 0 subscription response is extended with:

```json
{
  "...existing fields...": "...",
  "seatCount": 5,
  "accountCreditBalance": { "amount": 15.00, "currency": "USD" },
  "addons": [
    {
      "id": 1,
      "addon": { "id": 1, "name": "Priority Support" },
      "quantity": 1,
      "status": "ACTIVE",
      "attachedAt": "2025-01-15T10:00:00Z"
    }
  ]
}
```

---

## 7. External Dependencies (Phase 1)

### Payment Gateway (extended)

Phase 0 payment gateway is extended with:

**Refund operation:**
- Input: original transaction ID (string), refund amount (Money)
- Output on success: refund transaction ID (string) and processed timestamp
- Output on failure: error reason (declined, timeout, original_not_found, etc.)

---

## 8. Business Rules Summary (Phase 1)

Phase 0 rules 1-20 remain in effect. Phase 1 adds:

21. A subscription may have at most 5 active add-ons at any time.
22. The same add-on cannot be attached twice to the same subscription.
23. Add-on currency must match the subscription's plan currency.
24. Add-on must be compatible with the subscription's current plan tier.
25. PER_SEAT add-ons can only be attached to per-seat plan subscriptions.
26. Mid-cycle add-on attachment generates prorated charge (HALF_UP rounding).
27. Mid-cycle add-on detachment generates prorated credit.
28. Plan changes auto-detach incompatible add-ons with prorated credit.
29. FREE tier plans cannot have per-seat pricing.
30. Per-seat plans must define minimum seats >= 1.
31. Seat count must be within plan's [minSeats, maxSeats] range.
32. Seat increase charges immediately (like plan upgrade); seat decrease credits (like downgrade).
33. Seat changes auto-update PER_SEAT add-on quantities and generate add-on proration.
34. Credit notes can only be issued for Paid invoices.
35. Total credit notes for an invoice cannot exceed the invoice total.
36. REFUND_TO_PAYMENT processes refund via payment gateway.
37. ACCOUNT_CREDIT adds to subscription's credit balance, applied on next renewal.
38. Account credit is applied after discount but before payment gateway charge during renewal.
39. Account credit applied to an invoice cannot exceed the invoice total after discount.
40. Changing from per-seat to non-per-seat plan sets seat count to null and detaches PER_SEAT add-ons.
