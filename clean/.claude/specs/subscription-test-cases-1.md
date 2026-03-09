# Test Cases: Subscription Management API — Phase 1

All test cases are derived from subscription-api-1.md (Phase 1). Tests cover the new domain models, state machines, use cases (UC-9 through UC-12), extended Phase 0 use cases, and cross-feature interactions. Section numbering continues from Phase 0 (§17+).

---

## 17. Value Object / Validation Tests (Phase 1)

### 17.1 AddOn

| # | Case | Input | Expected |
|---|---|---|---|
| AO-V1 | Valid FLAT add-on | name="Priority Support", price=USD(9.99), billingType=FLAT, tiers={STARTER,PROFESSIONAL}, active=true | Success |
| AO-V2 | Valid PER_SEAT add-on | name="Extra Storage", price=USD(2.00), billingType=PER_SEAT, tiers={PROFESSIONAL,ENTERPRISE} | Success |
| AO-V3 | Blank name | name="" | Error: must not be blank |
| AO-V4 | Zero price | price=USD(0.00) | Error: must be greater than zero |
| AO-V5 | Negative price | price=USD(-5.00) | Error: must be greater than zero |
| AO-V6 | Empty compatible tiers | tiers={} | Error: must not be empty |
| AO-V7 | Valid JPY add-on | price=JPY(500), billingType=FLAT | Success, scale=0 |
| AO-V8 | JPY rejects decimals | price=JPY(9.99) | Error: JPY cannot have decimal places |

### 17.2 CreditNote

| # | Case | Input | Expected |
|---|---|---|---|
| CN-V1 | Valid full refund | type=FULL, application=REFUND_TO_PAYMENT, reason="Customer request" | Success |
| CN-V2 | Valid partial refund | type=PARTIAL, amount=USD(25.00), application=ACCOUNT_CREDIT, reason="Compensation" | Success |
| CN-V3 | Partial with zero amount | type=PARTIAL, amount=USD(0.00) | Error: must be greater than zero |
| CN-V4 | Partial with negative amount | type=PARTIAL, amount=USD(-10.00) | Error: must be greater than zero |
| CN-V5 | Blank reason | reason="" | Error: must not be blank |
| CN-V6 | Valid JPY credit note | type=PARTIAL, amount=JPY(1000) | Success |

### 17.3 Plan (extended — seat fields)

| # | Case | Input | Expected |
|---|---|---|---|
| PL-V1 | Valid per-seat plan | perSeatPricing=true, minSeats=1, maxSeats=100 | Success |
| PL-V2 | Per-seat with no max | perSeatPricing=true, minSeats=1, maxSeats=null | Success (unlimited) |
| PL-V3 | Per-seat min > max | perSeatPricing=true, minSeats=10, maxSeats=5 | Error: max must be >= min |
| PL-V4 | Per-seat min zero | perSeatPricing=true, minSeats=0 | Error: minSeats must be at least 1 |
| PL-V5 | FREE tier per-seat | tier=FREE, perSeatPricing=true | Error: FREE tier cannot have per-seat pricing |
| PL-V6 | Non-per-seat ignores seats | perSeatPricing=false, minSeats=5, maxSeats=10 | Success (seat fields ignored) |

### 17.4 Subscription Seat Count

| # | Case | Input | Expected |
|---|---|---|---|
| SC-V1 | Valid seat count | seatCount=5, plan.minSeats=1, plan.maxSeats=100 | Success |
| SC-V2 | At minimum | seatCount=1, plan.minSeats=1 | Success |
| SC-V3 | At maximum | seatCount=100, plan.maxSeats=100 | Success |
| SC-V4 | Below minimum | seatCount=0, plan.minSeats=1 | Error: below minimum |
| SC-V5 | Above maximum | seatCount=101, plan.maxSeats=100 | Error: above maximum |
| SC-V6 | Null for non-per-seat | seatCount=null, perSeatPricing=false | Success |

---

## 18. New State Machine Tests

### 18.1 SubscriptionAddOn Status

| # | From | To | Trigger | Expected |
|---|---|---|---|---|
| SA-V1 | Active | Detached | Customer detaches | Status is Detached, detachedAt set |
| SA-V2 | Active | Detached | Plan change incompatibility | Status is Detached |
| SA-V3 | Active | Detached | Subscription canceled | Status is Detached |

### 18.2 SubscriptionAddOn Invalid Transitions

| # | From | Attempted To | Expected |
|---|---|---|---|
| SA-I1 | Detached | Active | Error: Detached is terminal |

### 18.3 CreditNote Status

| # | From | To | Trigger | Expected |
|---|---|---|---|---|
| CN-S1 | Issued | Applied | Refund succeeds (REFUND_TO_PAYMENT) | Status is Applied, refundTransactionId set |
| CN-S2 | Issued | Applied | ACCOUNT_CREDIT (immediate) | Status is Applied |
| CN-S3 | Issued | Voided | Admin voids | Status is Voided |

### 18.4 CreditNote Invalid Transitions

| # | From | Attempted To | Expected |
|---|---|---|---|
| CN-I1 | Applied | any state | Error: Applied is terminal |
| CN-I2 | Voided | any state | Error: Voided is terminal |
| CN-I3 | Issued | Issued | Error: no self-transition |

---

## 19. UC-9: Attach Add-on Tests

### 19.1 Happy path

| # | Case | Setup | Input | Expected |
|---|---|---|---|---|
| AO-H1 | Attach FLAT add-on | Active sub (PROFESSIONAL), add-on compatible, 15 of 30 days remaining | addonId=valid | SubscriptionAddOn created, qty=1, prorated charge = price * 15/30 |
| AO-H2 | Attach PER_SEAT add-on | Active sub (PROFESSIONAL, 5 seats), per-seat add-on, 15/30 days remaining | addonId=perSeat | qty=5, prorated charge = price * 5 * 15/30 |
| AO-H3 | Attach on first day of period | Active sub, 30 of 30 days remaining | addonId=valid | Prorated charge = full price |
| AO-H4 | Attach on last day of period | Active sub, 1 of 30 days remaining | addonId=valid | Prorated charge = price * 1/30 |
| AO-H5 | Attach 5th add-on (at limit) | Active sub with 4 existing add-ons | addonId=valid | Success, now at max |
| AO-H6 | Attach JPY add-on | Active sub (JPY plan), JPY add-on, 17/30 days remaining | addonId=jpyAddon | JPY proration rounded to integer |

### 19.2 Proration calculation

| # | Case | Setup | Expected |
|---|---|---|---|
| AO-P1 | FLAT half-cycle | USD(9.99) add-on, 15/30 remaining | charge = USD(5.00) — HALF_UP |
| AO-P2 | FLAT one-third cycle | USD(9.99) add-on, 10/30 remaining | charge = USD(3.33) — 9.99*10/30=3.33 |
| AO-P3 | PER_SEAT 5 seats half-cycle | USD(2.00) per-seat, 5 seats, 15/30 remaining | charge = USD(5.00) — 2.00*5*15/30 |
| AO-P4 | JPY rounding | JPY(500) add-on, 17/30 remaining | charge = JPY(283) — 500*17/30=283.33→283 HALF_UP |

### 19.3 Validation and business rule errors

| # | Case | Setup | Input | Expected |
|---|---|---|---|---|
| AO-E1 | Subscription not found | No subscription | subId=999 | 404 error |
| AO-E2 | Subscription not Active (Paused) | Paused subscription | subId=paused | 409 error |
| AO-E3 | Subscription not Active (Trial) | Trial subscription | subId=trial | 409 error |
| AO-E4 | Add-on not found | No add-on | addonId=999 | 404 error |
| AO-E5 | Add-on inactive | Inactive add-on | addonId=inactive | 404 error |
| AO-E6 | Currency mismatch | USD subscription, EUR add-on | addonId=eurAddon | 409 error |
| AO-E7 | Tier incompatibility | STARTER subscription, add-on for {PROFESSIONAL, ENTERPRISE} | addonId=proOnly | 409 error |
| AO-E8 | PER_SEAT on non-per-seat plan | Non-per-seat plan | addonId=perSeat | 409 error |
| AO-E9 | Duplicate add-on | Add-on already attached | addonId=existing | 409 error |
| AO-E10 | Add-on limit reached | 5 active add-ons already | addonId=valid | 409 error: limit reached |
| AO-E11 | Payment fails | Gateway declines | addonId=valid | 502 error, add-on NOT attached |
| AO-E12 | Invalid subscription ID | subId=-1 | — | 400 error |

---

## 20. UC-10: Detach Add-on Tests

### 20.1 Happy path

| # | Case | Setup | Input | Expected |
|---|---|---|---|---|
| DA-H1 | Detach FLAT add-on mid-cycle | Active sub, FLAT add-on, 15/30 remaining | addonId=valid | Detached, credit = price * 15/30 added to account balance |
| DA-H2 | Detach PER_SEAT add-on | Active sub, PER_SEAT add-on qty=5, 15/30 remaining | addonId=perSeat | credit = price * 5 * 15/30 |
| DA-H3 | Detach on last day | Active sub, 1/30 remaining | addonId=valid | Minimal credit (1/30 of price) |
| DA-H4 | Detach while Paused | Paused sub, 20 frozen days remaining, 30 day period | addonId=valid | credit = price * 20/30 |

### 20.2 Validation and business rule errors

| # | Case | Setup | Input | Expected |
|---|---|---|---|---|
| DA-E1 | Subscription not found | No subscription | subId=999 | 404 error |
| DA-E2 | Subscription in Trial | Trial subscription | subId=trial | 409 error |
| DA-E3 | Subscription Canceled | Canceled subscription | subId=canceled | 409 error |
| DA-E4 | Add-on not attached | No SubscriptionAddOn | addonId=999 | 404 error |
| DA-E5 | Add-on already detached | Detached SubscriptionAddOn | addonId=detached | 404 error |
| DA-E6 | Invalid subscription ID | subId=0 | — | 400 error |
| DA-E7 | Invalid add-on ID | addonId=-1 | — | 400 error |

---

## 21. UC-11: Update Seat Count Tests

### 21.1 Happy path — Seat increase (immediate charge)

| # | Case | Setup | Input | Expected |
|---|---|---|---|---|
| ST-U1 | Increase by 1 seat mid-cycle | Active, per-seat USD(10.00), 5 seats, 15/30 remaining | seatCount=6 | charge = 10.00 * 1 * 15/30 = USD(5.00), seats updated to 6 |
| ST-U2 | Increase by 5 seats | Active, per-seat USD(10.00), 5 seats, 15/30 remaining | seatCount=10 | charge = 10.00 * 5 * 15/30 = USD(25.00) |
| ST-U3 | Increase to maximum | Active, per-seat, 5 seats, maxSeats=10 | seatCount=10 | Success, seats=10 |
| ST-U4 | Increase with PER_SEAT add-on | Active, 5 seats, PER_SEAT add-on USD(2.00), 15/30 remaining | seatCount=7 | Seat proration + add-on proration for 2 extra seats |

### 21.2 Happy path — Seat decrease (credit)

| # | Case | Setup | Input | Expected |
|---|---|---|---|---|
| ST-D1 | Decrease by 1 seat | Active, per-seat USD(10.00), 5 seats, 15/30 remaining | seatCount=4 | credit = 10.00 * 1 * 15/30 = USD(5.00), added to account balance |
| ST-D2 | Decrease to minimum | Active, per-seat, 5 seats, minSeats=1 | seatCount=1 | Success, credit for 4 seats |
| ST-D3 | Decrease with PER_SEAT add-on | Active, 5 seats, PER_SEAT add-on USD(2.00), 15/30 remaining | seatCount=3 | Seat credit + add-on credit for 2 seats reduction |

### 21.3 Proration calculation

| # | Case | Setup | Expected |
|---|---|---|---|
| ST-P1 | One-third cycle remaining | USD(10.00) per seat, +3 seats, 10/30 remaining | charge = USD(10.00) — 10.00*3*10/30 |
| ST-P2 | HALF_UP rounding | USD(10.00) per seat, +1 seat, 17/30 remaining | charge = USD(5.67) — 10.00*1*17/30=5.6667→5.67 |
| ST-P3 | JPY rounding | JPY(1000) per seat, +2 seats, 17/30 remaining | charge = JPY(1133) — 1000*2*17/30=1133.33→1133 |

### 21.4 Validation and business rule errors

| # | Case | Setup | Input | Expected |
|---|---|---|---|---|
| ST-E1 | Subscription not found | No subscription | subId=999 | 404 error |
| ST-E2 | Subscription not Active | Paused subscription | subId=paused | 409 error |
| ST-E3 | Not a per-seat plan | Non-per-seat plan | seatCount=5 | 409 error |
| ST-E4 | Same seat count | 5 seats currently | seatCount=5 | 409 error |
| ST-E5 | Below minimum | minSeats=2 | seatCount=1 | 409 error |
| ST-E6 | Above maximum | maxSeats=10 | seatCount=11 | 409 error |
| ST-E7 | Zero seats | — | seatCount=0 | 400 error |
| ST-E8 | Negative seats | — | seatCount=-1 | 400 error |
| ST-E9 | Payment fails on increase | Gateway declines | seatCount=higher | 502 error, seats NOT changed |
| ST-E10 | Invalid subscription ID | subId=-1 | — | 400 error |

---

## 22. UC-12: Issue Credit Note Tests

### 22.1 Happy path

| # | Case | Setup | Input | Expected |
|---|---|---|---|---|
| CN-H1 | Full refund to payment | Paid invoice USD(49.99), no existing credits | type=FULL, app=REFUND_TO_PAYMENT | CreditNote amount=USD(49.99), Applied, refundTxId set |
| CN-H2 | Full refund as account credit | Paid invoice USD(49.99), no existing credits | type=FULL, app=ACCOUNT_CREDIT | CreditNote Applied, account balance += USD(49.99) |
| CN-H3 | Partial refund to payment | Paid invoice USD(49.99) | type=PARTIAL, amount=USD(20.00), app=REFUND_TO_PAYMENT | CreditNote USD(20.00), Applied |
| CN-H4 | Partial refund as credit | Paid invoice USD(49.99) | type=PARTIAL, amount=USD(10.00), app=ACCOUNT_CREDIT | account balance += USD(10.00) |
| CN-H5 | Second partial (remaining) | Paid invoice USD(49.99), existing credit USD(20.00) | type=PARTIAL, amount=USD(29.99) | Success, remaining = USD(0.00) |
| CN-H6 | Full after partial | Paid invoice USD(49.99), existing credit USD(20.00) | type=FULL | amount = USD(29.99) — remaining refundable |
| CN-H7 | JPY refund | Paid invoice JPY(5000) | type=FULL, app=ACCOUNT_CREDIT | CreditNote JPY(5000), Applied |

### 22.2 Validation and business rule errors

| # | Case | Setup | Input | Expected |
|---|---|---|---|---|
| CN-E1 | Invoice not found | No invoice | invoiceId=999 | 404 error |
| CN-E2 | Invoice not Paid (Open) | Open invoice | invoiceId=open | 409 error |
| CN-E3 | Invoice not Paid (Void) | Void invoice | invoiceId=void | 409 error |
| CN-E4 | Invoice not Paid (Uncollectible) | Uncollectible invoice | invoiceId=unc | 409 error |
| CN-E5 | Already fully refunded | USD(49.99) invoice, existing credit USD(49.99) | type=FULL | 409 error: no remaining |
| CN-E6 | Partial exceeds remaining | USD(49.99) invoice, existing credit USD(40.00) | type=PARTIAL, amount=USD(10.00) | 409 error: exceeds remaining USD(9.99) |
| CN-E7 | Partial zero amount | — | type=PARTIAL, amount=USD(0.00) | 400 error |
| CN-E8 | Partial negative amount | — | type=PARTIAL, amount=USD(-5.00) | 400 error |
| CN-E9 | Blank reason | — | reason="" | 400 error |
| CN-E10 | Gateway refund fails | Paid invoice | app=REFUND_TO_PAYMENT, gateway fails | 502 error, credit note stays Issued |
| CN-E11 | Invalid invoice ID | invoiceId=0 | — | 400 error |

---

## 23. Add-on + Renewal Interaction Tests

| # | Case | Setup | Expected |
|---|---|---|---|
| AR-1 | Renewal with FLAT add-on | Active sub, 1 FLAT add-on USD(9.99) | Invoice includes ADDON_CHARGE USD(9.99) |
| AR-2 | Renewal with PER_SEAT add-on | Active sub, 5 seats, PER_SEAT add-on USD(2.00) | Invoice includes ADDON_CHARGE USD(10.00) |
| AR-3 | Renewal with multiple add-ons | Active sub, 3 add-ons | Invoice includes 3 ADDON_CHARGE line items |
| AR-4 | Renewal with add-on + discount | Active sub, add-on USD(9.99), 20% discount | Discount applies to subtotal including add-on charges |
| AR-5 | Renewal with detached add-on | Active sub, 1 Active add-on, 1 Detached add-on | Only Active add-on charged |
| AR-6 | Per-seat renewal total | Active sub, per-seat USD(10.00), 5 seats | PLAN_CHARGE = USD(50.00) = 10.00 * 5 |
| AR-7 | Per-seat + PER_SEAT add-on | Active sub, per-seat USD(10.00), 5 seats, PER_SEAT add-on USD(2.00) | PLAN_CHARGE=USD(50.00) + ADDON_CHARGE=USD(10.00) |
| AR-8 | Renewal per-seat with discount | Per-seat USD(10.00), 5 seats, 20% discount | subtotal=USD(50.00), discount=USD(10.00), total=USD(40.00) |

---

## 24. Seat + Add-on Interaction Tests

| # | Case | Setup | Action | Expected |
|---|---|---|---|---|
| SA-1 | Seat increase updates PER_SEAT add-on qty | 5 seats, PER_SEAT add-on qty=5 | seatCount=7 | add-on qty updated to 7, add-on proration charged for +2 |
| SA-2 | Seat decrease updates PER_SEAT add-on qty | 5 seats, PER_SEAT add-on qty=5 | seatCount=3 | add-on qty updated to 3, add-on proration credited for -2 |
| SA-3 | Seat change does not affect FLAT add-on | 5 seats, FLAT add-on qty=1 | seatCount=10 | FLAT add-on qty stays 1, no add-on proration |
| SA-4 | Seat increase with multiple PER_SEAT add-ons | 5 seats, 2 PER_SEAT add-ons | seatCount=8 | Both add-ons qty updated, proration for both |
| SA-5 | Seat increase proration includes add-on lines | Per-seat USD(10.00), PER_SEAT add-on USD(2.00), 15/30 remaining | seatCount += 2 | Invoice: SEAT_PRORATION_CHARGE + ADDON_PRORATION_CHARGE |
| SA-6 | Seat decrease credit includes add-on credit | Per-seat USD(10.00), PER_SEAT add-on USD(2.00), 15/30 remaining | seatCount -= 2 | Account credit: seat credit + add-on credit |

---

## 25. Credit + Renewal Interaction Tests

| # | Case | Setup | Expected |
|---|---|---|---|
| CR-1 | Account credit applied to renewal | Account balance USD(15.00), renewal total USD(49.99) | ACCOUNT_CREDIT line item USD(-15.00), gateway charged USD(34.99), balance=USD(0.00) |
| CR-2 | Account credit exceeds renewal total | Account balance USD(60.00), renewal total USD(49.99) | ACCOUNT_CREDIT=USD(-49.99), gateway not charged, balance=USD(10.01) |
| CR-3 | Account credit covers exactly | Account balance USD(49.99), renewal total USD(49.99) | ACCOUNT_CREDIT=USD(-49.99), invoice auto-Paid, balance=USD(0.00) |
| CR-4 | Account credit with discount | Balance USD(10.00), subtotal USD(49.99), 20% discount | After discount: USD(39.99), credit applied: USD(10.00), charge: USD(29.99), balance=USD(0.00) |
| CR-5 | No account credit (balance zero) | Balance USD(0.00) | No ACCOUNT_CREDIT line item, normal charge |
| CR-6 | Account credit with zero-total after discount | Balance USD(50.00), subtotal USD(49.99), discount makes total USD(0.00) | No credit consumed, invoice auto-Paid, balance stays USD(50.00) |

---

## 26. Plan Change + Add-on Interaction Tests

| # | Case | Setup | Action | Expected |
|---|---|---|---|---|
| PC-1 | Add-on stays on compatible plan change | PROFESSIONAL→ENTERPRISE, add-on compatible with both | Change plan | Add-on remains Active |
| PC-2 | Add-on auto-detached on incompatible change | PROFESSIONAL→STARTER, add-on only for {PROFESSIONAL, ENTERPRISE} | Change plan | Add-on detached, proration credit generated |
| PC-3 | Multiple add-ons, partial incompatibility | 3 add-ons, 1 incompatible with new tier | Change plan | 1 detached with credit, 2 remain Active |
| PC-4 | PER_SEAT add-on detached on non-per-seat change | Per-seat plan → non-per-seat plan, PER_SEAT add-on | Change plan | PER_SEAT add-on detached, seat count set to null |
| PC-5 | Non-per-seat to per-seat: seat count initialized | Non-per-seat → per-seat plan (minSeats=3) | Change plan | seatCount set to 3 (minSeats) |
| PC-6 | Proration invoice includes add-on detach credits | Plan change with incompatible add-on, 15/30 remaining | Change plan | Invoice has PRORATION_CREDIT + PRORATION_CHARGE + ADDON_PRORATION_CREDIT |

---

## 27. Integration / API Tests (Phase 1)

### 27.1 Attach Add-on API

| # | Case | Request | Expected Response |
|---|---|---|---|
| API-AO1 | Successful attachment | POST /api/subscriptions/1/addons with valid body | 201, SubscriptionAddOn response |
| API-AO2 | Subscription not Active | POST on paused subscription | 409 |
| API-AO3 | Tier incompatible | POST with incompatible add-on | 409 |
| API-AO4 | Payment failure | POST, gateway fails | 502, add-on NOT attached |

### 27.2 Detach Add-on API

| # | Case | Request | Expected Response |
|---|---|---|---|
| API-DA1 | Successful detachment | DELETE /api/subscriptions/1/addons/1 | 200, credit applied |
| API-DA2 | Add-on not attached | DELETE non-existent | 404 |

### 27.3 Update Seat Count API

| # | Case | Request | Expected Response |
|---|---|---|---|
| API-ST1 | Increase seats | PUT /api/subscriptions/1/seats {seatCount: 10} | 200, seats updated |
| API-ST2 | Decrease seats | PUT /api/subscriptions/1/seats {seatCount: 3} | 200, credit applied |
| API-ST3 | Not per-seat plan | PUT on non-per-seat subscription | 409 |
| API-ST4 | Payment failure on increase | PUT, gateway fails | 502, seats NOT changed |
| API-ST5 | Below minimum | PUT {seatCount: 0} | 400 |
| API-ST6 | Above maximum | PUT {seatCount: 999} on maxSeats=10 plan | 409 |

### 27.4 Issue Credit Note API

| # | Case | Request | Expected Response |
|---|---|---|---|
| API-CN1 | Full refund to payment | POST /api/invoices/1/credit-notes {type: FULL, application: REFUND_TO_PAYMENT} | 201, Applied |
| API-CN2 | Partial as account credit | POST {type: PARTIAL, amount: 20.00, application: ACCOUNT_CREDIT} | 201, Applied |
| API-CN3 | Invoice not Paid | POST on Open invoice | 409 |
| API-CN4 | Gateway refund fails | POST with REFUND_TO_PAYMENT, gateway fails | 502, Issued |
| API-CN5 | Already fully refunded | POST on fully refunded invoice | 409 |

### 27.5 List Add-ons API

| # | Case | Request | Expected Response |
|---|---|---|---|
| API-LA1 | List subscription add-ons | GET /api/subscriptions/1/addons | 200, array of SubscriptionAddOn |
| API-LA2 | Empty list | GET on subscription with no add-ons | 200, empty array |
| API-LA3 | Invalid subscription ID | GET /api/subscriptions/-1/addons | 400 |

### 27.6 List Credit Notes API

| # | Case | Request | Expected Response |
|---|---|---|---|
| API-LC1 | List credit notes | GET /api/invoices/1/credit-notes | 200, array of CreditNote |
| API-LC2 | Empty list | GET on invoice with no credit notes | 200, empty array |
| API-LC3 | Invalid invoice ID | GET /api/invoices/-1/credit-notes | 400 |

---

## 28. UC-1 Extended: Create Subscription with Seats

| # | Case | Setup | Input | Expected |
|---|---|---|---|---|
| CS-S1 | Create with per-seat plan | Per-seat plan (minSeats=1, maxSeats=50) | seatCount=5 | Subscription created, seatCount=5 |
| CS-S2 | Create per-seat at minimum | Per-seat plan (minSeats=3) | seatCount=3 | Success |
| CS-S3 | Create per-seat below minimum | Per-seat plan (minSeats=3) | seatCount=2 | 400 error |
| CS-S4 | Create per-seat above maximum | Per-seat plan (maxSeats=10) | seatCount=11 | 400 error |
| CS-S5 | Create per-seat without seat count | Per-seat plan | seatCount=null | 400 error: required for per-seat plan |
| CS-S6 | Create non-per-seat with seat count | Non-per-seat plan | seatCount=5 | Success, seatCount set to null (ignored) |

---

## Summary: Phase 1 Test Case Count

| Section | Area | Count |
|---|---|---|
| §17 | Value Object / Validation (Phase 1) | 26 |
| §18 | New State Machines | 7 |
| §19 | UC-9: Attach Add-on | 22 |
| §20 | UC-10: Detach Add-on | 11 |
| §21 | UC-11: Update Seat Count | 20 |
| §22 | UC-12: Issue Credit Note | 18 |
| §23 | Add-on + Renewal Interaction | 8 |
| §24 | Seat + Add-on Interaction | 6 |
| §25 | Credit + Renewal Interaction | 6 |
| §26 | Plan Change + Add-on Interaction | 6 |
| §27 | Integration / API (Phase 1) | 20 |
| §28 | UC-1 Extended: Seats | 6 |
| **Total Phase 1** | | **156** |
| **Total Phase 0 + Phase 1** | | **389** |
