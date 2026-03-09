# Test Cases: Subscription Management API

All test cases are derived from subscription-api.md. Tests must cover validation, business rules, state transitions, proration calculations, and error handling for all use cases (UC-1 through UC-8).

---

## 1. Value Object / Validation Tests

### 1.1 Money (extended for negative and proration)

| # | Case | Input | Expected |
|---|---|---|---|
| V-M1 | Valid USD | amount=49.99, currency=USD | Success, scale=2 |
| V-M2 | Valid JPY | amount=5000, currency=JPY | Success, scale=0 |
| V-M3 | JPY rejects decimals | amount=99.99, currency=JPY | Error: JPY cannot have decimal places |
| V-M4 | Negative amount (valid for credits) | amount=-10.00, currency=USD | Success |
| V-M5 | Addition same currency | USD(49.99) + USD(15.00) | USD(64.99) |
| V-M6 | Addition cross-currency | USD(10.00) + EUR(5.00) | Error: currency mismatch |
| V-M7 | Subtraction resulting in negative | USD(10.00) - USD(30.00) | USD(-20.00) — valid for proration |
| V-M8 | Multiplication by days ratio | USD(49.99) * (15/30) | USD(25.00) — HALF_UP rounding |
| V-M9 | Zero amount is valid | amount=0.00, currency=USD | Success |
| V-M10 | HALF_UP rounding | USD(49.99) * (17/30) | USD(28.33) — 28.327... rounds to 28.33 |
| V-M11 | JPY rounding | JPY(4999) * (17/30) | JPY(2833) — rounds to integer |
| V-M12 | Negate operation | USD(49.99).negate() | USD(-49.99) |

### 1.2 Plan Tier

| # | Case | Input | Expected |
|---|---|---|---|
| V-T1 | Tier ordering: upgrade | from=STARTER, to=PROFESSIONAL | isUpgrade=true |
| V-T2 | Tier ordering: downgrade | from=PROFESSIONAL, to=STARTER | isUpgrade=false |
| V-T3 | Tier ordering: same | from=STARTER, to=STARTER | isSameTier=true |
| V-T4 | Tier ordering: FREE to any | from=FREE, to=STARTER | isUpgrade=true |
| V-T5 | Tier ordering: any to FREE | from=ENTERPRISE, to=FREE | isUpgrade=false |

### 1.3 Billing Interval

| # | Case | Input | Expected |
|---|---|---|---|
| V-BI1 | Monthly period calculation | start=2025-01-15, interval=MONTHLY | end=2025-02-15 |
| V-BI2 | Yearly period calculation | start=2025-01-15, interval=YEARLY | end=2026-01-15 |
| V-BI3 | Monthly end-of-month edge | start=2025-01-31, interval=MONTHLY | end=2025-02-28 |
| V-BI4 | Monthly leap year | start=2024-01-31, interval=MONTHLY | end=2024-02-29 |
| V-BI5 | Yearly leap day | start=2024-02-29, interval=YEARLY | end=2025-02-28 |

### 1.4 Subscription ID / Customer ID / Plan ID

| # | Case | Input | Expected |
|---|---|---|---|
| V-ID1 | Valid positive | 1 | Success |
| V-ID2 | Large positive | 9999999 | Success |
| V-ID3 | Zero | 0 | Error: must be positive |
| V-ID4 | Negative | -1 | Error: must be positive |

### 1.5 Discount

| # | Case | Input | Expected |
|---|---|---|---|
| V-D1 | Valid percentage | type=PERCENTAGE, value=20, durationMonths=3 | Success |
| V-D2 | Percentage at minimum | type=PERCENTAGE, value=1 | Success |
| V-D3 | Percentage at maximum | type=PERCENTAGE, value=100 | Success |
| V-D4 | Percentage below minimum | type=PERCENTAGE, value=0 | Error: must be at least 1 |
| V-D5 | Percentage above maximum | type=PERCENTAGE, value=101 | Error: must be at most 100 |
| V-D6 | Valid fixed amount | type=FIXED_AMOUNT, value=USD(10.00), durationMonths=6 | Success |
| V-D7 | Fixed amount zero | type=FIXED_AMOUNT, value=USD(0.00) | Error: must be positive |
| V-D8 | Duration at minimum | durationMonths=1 | Success |
| V-D9 | Duration at maximum | durationMonths=24 | Success |
| V-D10 | Duration null (forever) | durationMonths=null | Success |
| V-D11 | Duration above maximum | durationMonths=25 | Error: must be at most 24 |

### 1.6 Usage Record

| # | Case | Input | Expected |
|---|---|---|---|
| V-U1 | Valid usage | metric="api_calls", quantity=100, key="req-123" | Success |
| V-U2 | Minimum quantity | quantity=1 | Success |
| V-U3 | Zero quantity | quantity=0 | Error: must be at least 1 |
| V-U4 | Negative quantity | quantity=-5 | Error: must be at least 1 |
| V-U5 | Blank metric name | metric="" | Error: must not be blank |
| V-U6 | Blank idempotency key | key="" | Error: must not be blank |

---

## 2. Subscription State Machine Tests

### 2.1 Valid transitions

| # | From | To | Trigger | Expected |
|---|---|---|---|---|
| S-V1 | Trial | Active | Trial ends, payment succeeds | Status is Active, period advanced |
| S-V2 | Trial | Canceled | Customer cancels during trial | Status is Canceled, no charge |
| S-V3 | Trial | Expired | Trial ends, payment fails after grace | Status is Expired |
| S-V4 | Active | Paused | Customer pauses | Status is Paused, pausedAt is set |
| S-V5 | Active | PastDue | Renewal payment fails | Status is PastDue, gracePeriodEnd is set |
| S-V6 | Active | Canceled | Customer cancels immediately | Status is Canceled |
| S-V7 | Paused | Active | Customer resumes | Status is Active, pausedAt cleared |
| S-V8 | Paused | Canceled | Customer cancels while paused | Status is Canceled |
| S-V9 | Paused | Canceled | Auto-cancel after 30 days paused | Status is Canceled |
| S-V10 | PastDue | Active | Payment recovered within grace | Status is Active, gracePeriodEnd cleared |
| S-V11 | PastDue | Canceled | Grace period expires | Status is Canceled |

### 2.2 Invalid transitions

| # | From | Attempted To | Expected |
|---|---|---|---|
| S-I1 | Trial | Paused | Error: cannot pause during trial |
| S-I2 | Trial | PastDue | Error: invalid transition |
| S-I3 | Trial | Active | Error: only via trial end (not manual) — needs payment |
| S-I4 | Active | Trial | Error: cannot go back to trial |
| S-I5 | Active | Expired | Error: invalid transition |
| S-I6 | Paused | PastDue | Error: paused subscriptions don't bill |
| S-I7 | Paused | Trial | Error: cannot go back to trial |
| S-I8 | Paused | Expired | Error: invalid transition |
| S-I9 | PastDue | Paused | Error: cannot pause a past-due subscription |
| S-I10 | PastDue | Trial | Error: cannot go back to trial |
| S-I11 | PastDue | Expired | Error: invalid transition (PastDue → Canceled, not Expired) |
| S-I12 | Canceled | any state | Error: Canceled is terminal |
| S-I13 | Expired | any state | Error: Expired is terminal |

### 2.3 Invoice Status transitions (valid)

| # | From | To | Trigger | Expected |
|---|---|---|---|---|
| IS-V1 | Draft | Open | Invoice finalized | Status is Open |
| IS-V2 | Draft | Void | Invoice discarded | Status is Void |
| IS-V3 | Open | Paid | Payment succeeds | Status is Paid, paidAt set |
| IS-V4 | Open | Void | Subscription canceled | Status is Void |
| IS-V5 | Open | Uncollectible | 3 failed attempts | Status is Uncollectible |

### 2.4 Invoice Status transitions (invalid)

| # | From | Attempted To | Expected |
|---|---|---|---|
| IS-I1 | Open | Draft | Error: cannot revert to Draft |
| IS-I2 | Paid | any state | Error: Paid is terminal |
| IS-I3 | Void | any state | Error: Void is terminal |
| IS-I4 | Uncollectible | any state | Error: Uncollectible is terminal |
| IS-I5 | Draft | Paid | Error: must go through Open first |
| IS-I6 | Draft | Uncollectible | Error: must go through Open first |

---

## 3. UC-1: Create Subscription Tests

### 3.1 Happy path

| # | Case | Setup | Input | Expected |
|---|---|---|---|---|
| CS-H1 | Basic creation with trial | Plan "Pro" (MONTHLY, USD 49.99, active) | customerId=1, planId=pro | Subscription created, status=Trial, trialEnd=now+14d, no invoice generated |
| CS-H2 | Creation with discount | Plan "Pro", discount code "WELCOME20" (20%, 3 months) | customerId=1, planId=pro, discountCode="WELCOME20" | Subscription created with discount attached, remainingCycles=3 |
| CS-H3 | Creation with yearly plan | Plan "Pro Yearly" (YEARLY, USD 479.88) | customerId=1, planId=proYearly | Subscription created, periodEnd=now+14d (trial first) |
| CS-H4 | Creation with free tier | Plan "Free" (FREE tier, USD 0.00) | customerId=1, planId=free | Subscription created, status=Trial, no payment method required |

### 3.2 Validation errors

| # | Case | Input | Expected |
|---|---|---|---|
| CS-V1 | Invalid customer ID (zero) | customerId=0 | 400 error |
| CS-V2 | Invalid customer ID (negative) | customerId=-1 | 400 error |
| CS-V3 | Invalid plan ID (zero) | planId=0 | 400 error |

### 3.3 Business rule errors

| # | Case | Setup | Input | Expected |
|---|---|---|---|---|
| CS-B1 | Plan not found | No plan with ID 999 | planId=999 | 404 error |
| CS-B2 | Inactive plan | Plan exists but active=false | planId=inactive | 404 error (treat inactive as not available) |
| CS-B3 | Customer already has active subscription | Customer 1 has Active subscription | customerId=1 | 409 error: already subscribed |
| CS-B4 | Customer has Trial subscription | Customer 1 has Trial subscription | customerId=1 | 409 error: already subscribed |
| CS-B5 | Customer has Paused subscription | Customer 1 has Paused subscription | customerId=1 | 409 error: already subscribed |
| CS-B6 | Customer has PastDue subscription | Customer 1 has PastDue subscription | customerId=1 | 409 error: already subscribed |
| CS-B7 | Customer with Canceled subscription can re-subscribe | Customer 1 has Canceled subscription | customerId=1 | Success: new subscription created |
| CS-B8 | Invalid discount code | No discount with code "INVALID" | discountCode="INVALID" | 400 error |

---

## 4. UC-2: Change Plan Tests

### 4.1 Happy path — Upgrade (immediate charge)

| # | Case | Setup | Input | Expected |
|---|---|---|---|---|
| CP-U1 | Upgrade mid-cycle (day 15 of 30) | Active subscription, Starter USD(19.99), 15 days remaining | newPlanId=Professional USD(49.99) | Proration: credit=USD(10.00), charge=USD(25.00), net=USD(15.00), charged immediately |
| CP-U2 | Upgrade on first day | Active subscription, day 1 of 30 | newPlanId=higher | Credit ≈ full old price, charge ≈ full new price |
| CP-U3 | Upgrade on last day | Active subscription, day 30 of 30 (1 day remaining) | newPlanId=higher | Minimal proration (1/30 of each) |
| CP-U4 | Upgrade with JPY plan | Active, Starter JPY(1980), 15 days remaining | newPlanId=Professional JPY(4980) | JPY proration rounded to integer |

### 4.2 Happy path — Downgrade (credit applied)

| # | Case | Setup | Input | Expected |
|---|---|---|---|---|
| CP-D1 | Downgrade mid-cycle | Active, Professional USD(49.99), 15 days remaining | newPlanId=Starter USD(19.99) | Net proration is negative, credit stored for next invoice |
| CP-D2 | Downgrade to free tier | Active, Starter USD(19.99), 15 days remaining | newPlanId=Free USD(0.00) | Full credit for remaining days, no charge |

### 4.3 Proration calculation precision

| # | Case | Setup | Expected |
|---|---|---|---|
| CP-P1 | Exact half-cycle | USD(49.99), 15 of 30 days remaining | credit=USD(25.00), charge depends on new plan |
| CP-P2 | One-third cycle | USD(90.00), 10 of 30 days remaining | credit=USD(30.00) exactly |
| CP-P3 | Rounding scenario (HALF_UP) | USD(49.99), 17 of 30 days remaining | credit=USD(28.33) — 49.99*17/30=28.3277→28.33 |
| CP-P4 | Rounding scenario (HALF_UP boundary) | USD(100.00), 1 of 3 days remaining (yearly edge) | credit=USD(33.33) — 100*1/3=33.333→33.33 |
| CP-P5 | JPY rounding | JPY(4999), 17 of 30 days remaining | credit=JPY(2833) — 4999*17/30=2832.77→2833 |

### 4.4 Validation and business rule errors

| # | Case | Setup | Input | Expected |
|---|---|---|---|---|
| CP-E1 | Subscription not found | No subscription ID 999 | subId=999 | 404 error |
| CP-E2 | Subscription not Active | Subscription in Paused status | subId=paused | 409 error: must be Active |
| CP-E3 | Subscription in Trial | Subscription in Trial status | subId=trial | 409 error: must be Active |
| CP-E4 | Same plan | Active on Professional | newPlanId=Professional | 409 error: same plan |
| CP-E5 | New plan inactive | Plan exists but inactive | newPlanId=inactive | 404 error |
| CP-E6 | Currency mismatch | Active USD subscription | newPlanId with EUR | 409 error: currency mismatch |
| CP-E7 | Payment fails on upgrade | Active, upgrade, gateway fails | newPlanId=higher | 502 error, plan NOT changed |
| CP-E8 | Invalid subscription ID | subId=0 | — | 400 error |

---

## 5. UC-3: Pause Subscription Tests

### 5.1 Happy path

| # | Case | Setup | Input | Expected |
|---|---|---|---|---|
| PA-H1 | First pause in period | Active subscription, 0 prior pauses | subId=valid | Status=Paused, pausedAt=now |
| PA-H2 | Second pause in period | Active subscription, 1 prior pause | subId=valid | Status=Paused (allowed, limit is 2) |

### 5.2 Validation and business rule errors

| # | Case | Setup | Input | Expected |
|---|---|---|---|---|
| PA-E1 | Subscription not found | No subscription | subId=999 | 404 error |
| PA-E2 | Not in Active status (Trial) | Trial subscription | subId=trial | 409 error |
| PA-E3 | Not in Active status (Paused) | Already paused | subId=paused | 409 error |
| PA-E4 | Not in Active status (PastDue) | PastDue subscription | subId=pastDue | 409 error |
| PA-E5 | Not in Active status (Canceled) | Canceled subscription | subId=canceled | 409 error |
| PA-E6 | Pause limit reached | Active, already paused 2 times this period | subId=valid | 409 error: pause limit reached |
| PA-E7 | Invalid subscription ID | subId=-1 | — | 400 error |

---

## 6. UC-4: Resume Subscription Tests

### 6.1 Happy path

| # | Case | Setup | Input | Expected |
|---|---|---|---|---|
| RE-H1 | Resume after short pause | Paused 5 days ago, 20 days were remaining | subId=valid | Status=Active, periodEnd=now+20d, pausedAt=null |
| RE-H2 | Resume on pause day (same day) | Paused today, 15 days remaining | subId=valid | Status=Active, periodEnd=now+15d |

### 6.2 Period calculation

| # | Case | Setup | Expected |
|---|---|---|---|
| RE-P1 | Remaining days preserved | Paused with 10 days remaining, resumed 3 days later | periodEnd = resumeDate + 10 days (NOT resumeDate + 7) |
| RE-P2 | Full period remaining | Paused on first day, 30 days remaining | periodEnd = resumeDate + 30 days |
| RE-P3 | One day remaining | Paused with 1 day remaining | periodEnd = resumeDate + 1 day |

### 6.3 Validation and business rule errors

| # | Case | Setup | Input | Expected |
|---|---|---|---|---|
| RE-E1 | Subscription not found | No subscription | subId=999 | 404 error |
| RE-E2 | Not in Paused status (Active) | Active subscription | subId=active | 409 error |
| RE-E3 | Not in Paused status (Trial) | Trial subscription | subId=trial | 409 error |
| RE-E4 | Not in Paused status (PastDue) | PastDue subscription | subId=pastDue | 409 error |
| RE-E5 | Not in Paused status (Canceled) | Canceled subscription | subId=canceled | 409 error |
| RE-E6 | Invalid subscription ID | subId=0 | — | 400 error |

---

## 7. UC-5: Cancel Subscription Tests

### 7.1 Happy path — End-of-period cancellation

| # | Case | Setup | Input | Expected |
|---|---|---|---|---|
| CA-H1 | Cancel Active at period end | Active subscription | subId=valid, immediate=false | cancelAtPeriodEnd=true, canceledAt=now, status stays Active |
| CA-H2 | Cancel PastDue at period end | PastDue subscription | subId=valid, immediate=false | cancelAtPeriodEnd=true, canceledAt=now, status stays PastDue |

### 7.2 Happy path — Immediate cancellation

| # | Case | Setup | Input | Expected |
|---|---|---|---|---|
| CA-H3 | Cancel Active immediately | Active subscription, 1 open invoice | subId=valid, immediate=true | Status=Canceled, invoice voided |
| CA-H4 | Cancel Paused immediately | Paused subscription | subId=valid, immediate=true | Status=Canceled |
| CA-H5 | Cancel PastDue immediately | PastDue subscription, open invoice | subId=valid, immediate=true | Status=Canceled, invoice voided |
| CA-H6 | Cancel Trial immediately | Trial subscription | subId=valid, immediate=true | Status=Canceled, no charge |

### 7.3 Validation and business rule errors

| # | Case | Setup | Input | Expected |
|---|---|---|---|---|
| CA-E1 | Subscription not found | No subscription | subId=999 | 404 error |
| CA-E2 | Already Canceled | Canceled subscription | subId=canceled | 409 error |
| CA-E3 | Already Expired | Expired subscription | subId=expired | 409 error |
| CA-E4 | Invalid subscription ID | subId=-1 | — | 400 error |
| CA-E5 | End-of-period not available for Paused | Paused subscription | immediate=false | 409 error: paused subs can only be canceled immediately |

---

## 8. UC-6: Process Renewal Tests

### 8.1 Happy path

| # | Case | Setup | Expected |
|---|---|---|---|
| RN-H1 | Simple monthly renewal | Active, periodEnd <= now, plan USD(49.99), no usage | Invoice: [PLAN_CHARGE USD(49.99)], total=USD(49.99), paid, period advanced by 1 month |
| RN-H2 | Renewal with usage charges | Active, 500 API calls in period, rate=USD(0.01)/call | Invoice: [PLAN_CHARGE, USAGE_CHARGE USD(5.00)], total=planPrice+USD(5.00) |
| RN-H3 | Renewal with active discount | Active, plan USD(49.99), 20% discount, 2 remaining cycles | Invoice: subtotal=USD(49.99), discount=USD(10.00), total=USD(39.99), remainingCycles decremented to 1 |
| RN-H4 | Renewal with discount on last cycle | Active, plan USD(49.99), 20% discount, 1 remaining cycle | Discount applied, remainingCycles=0, discount expired after this cycle |
| RN-H5 | Renewal with zero-total invoice | Free tier plan, no usage charges | Invoice auto-marked as Paid, no charge attempt |
| RN-H6 | Yearly renewal | Active yearly plan, period ended | Period advanced by 1 year |
| RN-H7 | Renewal with credit from downgrade | Active, has pending credit USD(15.00), plan USD(49.99) | Invoice: planCharge=USD(49.99), credit applied, total=USD(34.99) |

### 8.2 Usage calculation

| # | Case | Setup | Expected |
|---|---|---|---|
| RN-U1 | Usage within period included | Usage records at day 5, 15, 25 of period | All 3 records included in USAGE_CHARGE |
| RN-U2 | Usage outside period excluded | Usage record from previous period | NOT included |
| RN-U3 | Usage on period boundary (start) | Usage recorded at period start timestamp | Included |
| RN-U4 | Usage on period boundary (end) | Usage recorded at period end timestamp | Excluded (end is exclusive) |

### 8.3 Payment failure → PastDue

| # | Case | Setup | Expected |
|---|---|---|---|
| RN-F1 | Payment fails on renewal | Active, payment gateway declines | Invoice status=Open, subscription status=PastDue, gracePeriodEnd=now+7d |
| RN-F2 | Payment timeout on renewal | Active, gateway times out | Same as RN-F1: PastDue with grace period |

### 8.4 Skip conditions

| # | Case | Setup | Expected |
|---|---|---|---|
| RN-S1 | Period not ended | Active, periodEnd > now | No-op, no invoice generated |
| RN-S2 | Not Active status | Paused subscription | No-op |
| RN-S3 | Canceled subscription | Canceled subscription | No-op |

---

## 9. UC-7: Record Usage Tests

### 9.1 Happy path

| # | Case | Setup | Input | Expected |
|---|---|---|---|---|
| US-H1 | Record API usage | Active subscription, limit=10000 | metric="api_calls", qty=100, key="req-1" | Usage recorded, total period usage=100 |
| US-H2 | Record at limit boundary | Active, limit=1000, existing usage=900 | qty=100 | Success, total=1000 (exactly at limit) |
| US-H3 | Idempotent duplicate | Existing record with key="req-1" | same key="req-1" | Returns existing record, no duplicate created |
| US-H4 | Unlimited plan (no limit) | Active, plan usageLimit=null | qty=999999 | Success (no limit to enforce) |

### 9.2 Validation errors

| # | Case | Input | Expected |
|---|---|---|---|
| US-V1 | Invalid subscription ID | subId=0 | 400 error |
| US-V2 | Zero quantity | qty=0 | 400 error |
| US-V3 | Negative quantity | qty=-1 | 400 error |
| US-V4 | Blank metric name | metric="" | 400 error |
| US-V5 | Blank idempotency key | key="" | 400 error |

### 9.3 Business rule errors

| # | Case | Setup | Input | Expected |
|---|---|---|---|---|
| US-B1 | Subscription not found | No subscription | subId=999 | 404 error |
| US-B2 | Subscription not Active (Paused) | Paused subscription | subId=paused | 409 error |
| US-B3 | Subscription not Active (Trial) | Trial subscription | subId=trial | 409 error |
| US-B4 | Subscription not Active (Canceled) | Canceled subscription | subId=canceled | 409 error |
| US-B5 | Usage limit exceeded | Active, limit=1000, existing usage=950 | qty=51 | 409 error: exceeds limit (950+51=1001 > 1000) |
| US-B6 | Usage limit exceeded by 1 | Active, limit=1000, existing usage=1000 | qty=1 | 409 error: already at limit |

---

## 10. UC-8: Recover Payment Tests

### 10.1 Happy path

| # | Case | Setup | Input | Expected |
|---|---|---|---|---|
| RP-H1 | Successful recovery (1st attempt) | Open invoice, PastDue sub, gracePeriod not expired, attempts=1 | invoiceId=valid | Invoice Paid, subscription Active, gracePeriod cleared |
| RP-H2 | Successful recovery (2nd attempt) | Open invoice, attempts=2 | invoiceId=valid | Invoice Paid, subscription Active |

### 10.2 Payment failure handling

| # | Case | Setup | Expected |
|---|---|---|---|
| RP-F1 | 1st retry failure | Open invoice, attempts=1, grace period valid | attempts=2, invoice stays Open, subscription stays PastDue |
| RP-F2 | 2nd retry failure | Open invoice, attempts=2, grace period valid | attempts=3, invoice Uncollectible, subscription Canceled |
| RP-F3 | 3rd attempt (max reached) | Open invoice, attempts=2, payment fails | After this failure: attempts=3 → Uncollectible + Canceled |

### 10.3 Grace period enforcement

| # | Case | Setup | Expected |
|---|---|---|---|
| RP-G1 | Within grace period (day 1) | gracePeriodEnd = now + 6 days | Allowed to attempt payment |
| RP-G2 | Within grace period (day 7) | gracePeriodEnd = now (exact boundary) | Allowed (boundary inclusive) |
| RP-G3 | Grace period expired (day 8) | gracePeriodEnd = yesterday | 409 error, subscription → Canceled, invoice → Uncollectible |
| RP-G4 | Grace period expired long ago | gracePeriodEnd = 30 days ago | 409 error, subscription → Canceled |

### 10.4 Validation and business rule errors

| # | Case | Setup | Input | Expected |
|---|---|---|---|---|
| RP-E1 | Invoice not found | No invoice | invoiceId=999 | 404 error |
| RP-E2 | Invoice not in Open status (Paid) | Paid invoice | invoiceId=paid | 409 error |
| RP-E3 | Invoice not in Open status (Void) | Voided invoice | invoiceId=void | 409 error |
| RP-E4 | Invoice not in Open status (Uncollectible) | Uncollectible invoice | invoiceId=unc | 409 error |
| RP-E5 | Subscription not PastDue | Active subscription with Open invoice | invoiceId=valid | 409 error |
| RP-E6 | Invalid invoice ID | invoiceId=0 | — | 400 error |
| RP-E7 | Payment gateway failure | Grace valid, Open invoice | — | 502 error, attempts incremented |

---

## 11. Pause/Resume Interaction Tests

These tests verify complex interactions between pause, resume, and billing period logic.

| # | Case | Setup | Action Sequence | Expected |
|---|---|---|---|---|
| PR-1 | Pause and resume preserves days | Active, 20 days remaining | Pause → wait 5 days → Resume | periodEnd = resumeDate + 20 days |
| PR-2 | Double pause in same period | Active, 0 prior pauses | Pause → Resume → Pause → Resume | Both pauses succeed (limit=2) |
| PR-3 | Triple pause in same period | Active, 2 prior pauses | Pause (3rd attempt) | 409 error: pause limit reached |
| PR-4 | Pause count resets on new period | Active, 2 pauses in previous period, now in new period | Pause | Success (count reset for new period) |
| PR-5 | Auto-cancel after 30 days paused | Paused 30 days ago | System check | Subscription → Canceled |
| PR-6 | Auto-cancel at exactly 30 days | Paused exactly 30 days ago | System check | Canceled (boundary inclusive) |
| PR-7 | No auto-cancel at 29 days | Paused 29 days ago | System check | Still Paused |

---

## 12. Discount Interaction Tests

| # | Case | Setup | Action | Expected |
|---|---|---|---|---|
| DI-1 | Discount applied to renewal | Active, 20% discount, 3 remaining | Process renewal | subtotal * 0.8, remainingCycles=2 |
| DI-2 | Discount expires after last cycle | Active, 10% discount, 1 remaining | Process renewal | Discount applied, then expired (remaining=0) |
| DI-3 | Discount NOT applied to proration | Active, 20% discount | Change plan (upgrade) | Proration invoice has no discount applied |
| DI-4 | Fixed discount capped at subtotal | Active, FIXED_AMOUNT USD(100.00), plan USD(49.99) | Process renewal | Discount = USD(49.99), total = USD(0.00), auto-Paid |
| DI-5 | Percentage discount with usage | Active, 20% discount, plan + usage charges | Process renewal | Discount applies to full subtotal (plan + usage) |
| DI-6 | Forever discount | Active, 20% discount, durationMonths=null | Process 3 renewals | Discount applied to all 3, remainingCycles stays null |

---

## 13. Trial → Active Conversion Tests

| # | Case | Setup | Expected |
|---|---|---|---|
| TR-1 | Successful trial conversion | Trial ends, payment succeeds | Status=Active, period start=trialEnd, invoice generated and Paid |
| TR-2 | Trial conversion with discount | Trial ends, 20% discount attached | First invoice has discount applied |
| TR-3 | Trial conversion payment fails | Trial ends, payment fails | Status=PastDue (not Expired immediately), grace period starts |
| TR-4 | Trial conversion grace expires | Trial ended, payment fails, 7 days pass | Status=Expired |
| TR-5 | Cancel during trial | Trial subscription | Cancel → Canceled, no invoice, no charge |
| TR-6 | Trial period exact boundary | Now = trialEnd exactly | Trial should end, conversion attempted |
| TR-7 | Trial not yet ended | Now = trialEnd - 1 second | No conversion, still Trial |

---

## 14. End-of-Period Cancellation Tests

| # | Case | Setup | Expected |
|---|---|---|---|
| EP-1 | Cancel at period end, then period ends | Active, cancelAtPeriodEnd=true | On renewal: Canceled (not renewed) |
| EP-2 | Cancel at period end, then change mind | Active, cancelAtPeriodEnd=true | Customer can clear cancelAtPeriodEnd (re-activate intent) |
| EP-3 | Cancel at period end with open invoice | Active, cancelAtPeriodEnd=true, paid invoice exists | Status→Canceled at period end, no new invoice |
| EP-4 | Immediate cancel overrides end-of-period | Active, cancelAtPeriodEnd=true | immediate=true → Canceled now, don't wait |

---

## 15. Integration / API Tests

### 15.1 Create Subscription API

| # | Case | Request | Expected Response |
|---|---|---|---|
| API-CS1 | Successful creation | POST /api/subscriptions with valid body | 201, status=Trial |
| API-CS2 | Validation failure | POST /api/subscriptions with customerId=0 | 400 |
| API-CS3 | Duplicate subscription | POST /api/subscriptions for customer with active sub | 409 |

### 15.2 Change Plan API

| # | Case | Request | Expected Response |
|---|---|---|---|
| API-CP1 | Successful upgrade | PUT /api/subscriptions/1/plan with higher plan | 200, plan updated, proration invoice created |
| API-CP2 | Payment failure on upgrade | PUT /api/subscriptions/1/plan, gateway fails | 502, plan NOT changed |
| API-CP3 | Invalid state | PUT /api/subscriptions/1/plan on paused sub | 409 |

### 15.3 Pause/Resume API

| # | Case | Request | Expected Response |
|---|---|---|---|
| API-PA1 | Successful pause | POST /api/subscriptions/1/pause | 200, status=Paused |
| API-RE1 | Successful resume | POST /api/subscriptions/1/resume | 200, status=Active |
| API-PA2 | Pause limit reached | POST /api/subscriptions/1/pause (3rd time) | 409 |

### 15.4 Cancel API

| # | Case | Request | Expected Response |
|---|---|---|---|
| API-CA1 | End-of-period cancel | POST /api/subscriptions/1/cancel {immediate:false} | 200, cancelAtPeriodEnd=true |
| API-CA2 | Immediate cancel | POST /api/subscriptions/1/cancel {immediate:true} | 200, status=Canceled |
| API-CA3 | Already canceled | POST /api/subscriptions/1/cancel on canceled sub | 409 |

### 15.5 Record Usage API

| # | Case | Request | Expected Response |
|---|---|---|---|
| API-US1 | Record usage | POST /api/subscriptions/1/usage with valid body | 201, usage recorded |
| API-US2 | Idempotent retry | POST same idempotency key twice | 201 both times, same record returned |
| API-US3 | Limit exceeded | POST usage that exceeds plan limit | 409 |

### 15.6 Recover Payment API

| # | Case | Request | Expected Response |
|---|---|---|---|
| API-RP1 | Successful recovery | POST /api/invoices/1/pay | 200, invoice Paid |
| API-RP2 | Grace expired | POST /api/invoices/1/pay after grace period | 409 |
| API-RP3 | Gateway failure | POST /api/invoices/1/pay, gateway fails | 502 |

### 15.7 Get/List APIs

| # | Case | Request | Expected Response |
|---|---|---|---|
| API-G1 | Get subscription | GET /api/subscriptions/1 | 200, full subscription response |
| API-G2 | Subscription not found | GET /api/subscriptions/99999 | 404 |
| API-G3 | List by customer | GET /api/subscriptions?customerId=1 | 200, array of subscriptions |
| API-G4 | List invoices | GET /api/invoices?subscriptionId=1 | 200, array of invoices |
| API-G5 | Invalid ID | GET /api/subscriptions/-1 | 400 |

---

## 16. Test Metrics to Capture

After implementing all tests, record the following metrics for comparison:

1. **Total number of test cases implemented**
2. **Test execution time** for domain/value object tests only (§1, §2)
3. **Test execution time** for use case / service tests only (§3-§14)
4. **Test execution time** for API / integration tests (§15)
5. **Total test execution time** (all tests)
6. **Number of tests requiring Spring context or database**
7. **Number of tests that are pure unit tests (no framework, no mocking framework)**
8. **Lines of test code**
9. **Lines of test setup / boilerplate** (context config, mock setup, container setup)

---

## Summary: Test Case Count

| Section | Area | Count |
|---|---|---|
| §1 | Value Object / Validation | 42 |
| §2 | Subscription State Machine | 24 |
| §2 | Invoice State Machine | 11 |
| §3 | UC-1: Create Subscription | 15 |
| §4 | UC-2: Change Plan | 19 |
| §5 | UC-3: Pause Subscription | 9 |
| §6 | UC-4: Resume Subscription | 9 |
| §7 | UC-5: Cancel Subscription | 11 |
| §8 | UC-6: Process Renewal | 17 |
| §9 | UC-7: Record Usage | 15 |
| §10 | UC-8: Recover Payment | 17 |
| §11 | Pause/Resume Interaction | 7 |
| §12 | Discount Interaction | 6 |
| §13 | Trial Conversion | 7 |
| §14 | End-of-Period Cancellation | 4 |
| §15 | Integration / API | 20 |
| **Total** | | **233** |
