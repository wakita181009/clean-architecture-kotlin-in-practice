package com.wakita181009.classic.service

import com.wakita181009.classic.dto.CreateSubscriptionRequest
import com.wakita181009.classic.exception.SeatCountOutOfRangeException
import com.wakita181009.classic.exception.SeatCountRequiredException
import com.wakita181009.classic.model.AddOn
import com.wakita181009.classic.model.BillingInterval
import com.wakita181009.classic.model.BillingType
import com.wakita181009.classic.model.Invoice
import com.wakita181009.classic.model.LineItemType
import com.wakita181009.classic.model.Money
import com.wakita181009.classic.model.Plan
import com.wakita181009.classic.model.PlanTier
import com.wakita181009.classic.model.Subscription
import com.wakita181009.classic.model.SubscriptionAddOn
import com.wakita181009.classic.model.SubscriptionAddOnStatus
import com.wakita181009.classic.model.SubscriptionStatus
import com.wakita181009.classic.repository.DiscountRepository
import com.wakita181009.classic.repository.InvoiceRepository
import com.wakita181009.classic.repository.PlanRepository
import com.wakita181009.classic.repository.SubscriptionAddOnRepository
import com.wakita181009.classic.repository.SubscriptionRepository
import com.wakita181009.classic.repository.UsageRecordRepository
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

@ExtendWith(MockKExtension::class)
class SubscriptionServicePhase1Test {
    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private val planRepository = mockk<PlanRepository>()
    private val invoiceRepository = mockk<InvoiceRepository>()
    private val usageRecordRepository = mockk<UsageRecordRepository>()
    private val discountRepository = mockk<DiscountRepository>()
    private val paymentGateway = mockk<PaymentGateway>()
    private val subscriptionAddOnRepository = mockk<SubscriptionAddOnRepository>()
    private val fixedInstant = Instant.parse("2025-01-15T00:00:00Z")
    private val clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    private lateinit var service: SubscriptionService

    @BeforeEach
    fun setUp() {
        service =
            SubscriptionService(
                subscriptionRepository = subscriptionRepository,
                planRepository = planRepository,
                invoiceRepository = invoiceRepository,
                usageRecordRepository = usageRecordRepository,
                discountRepository = discountRepository,
                paymentGateway = paymentGateway,
                clock = clock,
                subscriptionAddOnRepository = subscriptionAddOnRepository,
            )
    }

    private fun perSeatPlan(
        id: Long = 1L,
        basePrice: Money = Money(BigDecimal("10.00"), Money.Currency.USD),
        minimumSeats: Int = 1,
        maximumSeats: Int? = 50,
        tier: PlanTier = PlanTier.PROFESSIONAL,
    ) = Plan(
        id = id,
        name = "Team",
        billingInterval = BillingInterval.MONTHLY,
        basePrice = basePrice,
        tier = tier,
        features = setOf("feature1"),
        perSeatPricing = true,
        minimumSeats = minimumSeats,
        maximumSeats = maximumSeats,
    )

    private fun nonPerSeatPlan(
        id: Long = 2L,
        tier: PlanTier = PlanTier.PROFESSIONAL,
    ) = Plan(
        id = id,
        name = "Professional",
        billingInterval = BillingInterval.MONTHLY,
        basePrice = Money(BigDecimal("49.99"), Money.Currency.USD),
        tier = tier,
        features = setOf("feature1"),
    )

    private fun sampleSubscription(
        id: Long = 1L,
        plan: Plan = nonPerSeatPlan(),
        status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
        seatCount: Int? = null,
        accountCreditBalance: Money = Money.zero(plan.basePrice.currency),
        currentPeriodStart: Instant = Instant.parse("2025-01-01T00:00:00Z"),
        currentPeriodEnd: Instant = Instant.parse("2025-01-31T00:00:00Z"),
    ) = Subscription(
        id = id,
        customerId = 1L,
        plan = plan,
        status = status,
        currentPeriodStart = currentPeriodStart,
        currentPeriodEnd = currentPeriodEnd,
        seatCount = seatCount,
        accountCreditBalance = accountCreditBalance,
    )

    @Nested
    inner class CreateSubscriptionWithSeats {
        // CS-S1: Create with per-seat plan
        @Test
        fun `create with per-seat plan sets seat count`() {
            val plan = perSeatPlan()
            every { planRepository.findByIdAndActiveTrue(1L) } returns plan
            every { subscriptionRepository.findByCustomerIdAndStatusIn(1L, any()) } returns emptyList()
            every { subscriptionRepository.save(any()) } answers { firstArg() }

            val result =
                service.createSubscription(
                    CreateSubscriptionRequest(customerId = 1L, planId = 1L, paymentMethod = "CREDIT_CARD", seatCount = 5),
                )

            assertEquals(5, result.seatCount)
        }

        // CS-S2: Create per-seat at minimum
        @Test
        fun `create per-seat at minimum succeeds`() {
            val plan = perSeatPlan(minimumSeats = 3)
            every { planRepository.findByIdAndActiveTrue(1L) } returns plan
            every { subscriptionRepository.findByCustomerIdAndStatusIn(1L, any()) } returns emptyList()
            every { subscriptionRepository.save(any()) } answers { firstArg() }

            val result =
                service.createSubscription(
                    CreateSubscriptionRequest(customerId = 1L, planId = 1L, paymentMethod = "CREDIT_CARD", seatCount = 3),
                )

            assertEquals(3, result.seatCount)
        }

        // CS-S3: Create per-seat below minimum
        @Test
        fun `create per-seat below minimum throws`() {
            val plan = perSeatPlan(minimumSeats = 3)
            every { planRepository.findByIdAndActiveTrue(1L) } returns plan
            every { subscriptionRepository.findByCustomerIdAndStatusIn(1L, any()) } returns emptyList()

            assertThrows(SeatCountOutOfRangeException::class.java) {
                service.createSubscription(
                    CreateSubscriptionRequest(customerId = 1L, planId = 1L, paymentMethod = "CREDIT_CARD", seatCount = 2),
                )
            }
        }

        // CS-S4: Create per-seat above maximum
        @Test
        fun `create per-seat above maximum throws`() {
            val plan = perSeatPlan(maximumSeats = 10)
            every { planRepository.findByIdAndActiveTrue(1L) } returns plan
            every { subscriptionRepository.findByCustomerIdAndStatusIn(1L, any()) } returns emptyList()

            assertThrows(SeatCountOutOfRangeException::class.java) {
                service.createSubscription(
                    CreateSubscriptionRequest(customerId = 1L, planId = 1L, paymentMethod = "CREDIT_CARD", seatCount = 11),
                )
            }
        }

        // CS-S5: Create per-seat without seat count
        @Test
        fun `create per-seat without seat count throws`() {
            val plan = perSeatPlan()
            every { planRepository.findByIdAndActiveTrue(1L) } returns plan
            every { subscriptionRepository.findByCustomerIdAndStatusIn(1L, any()) } returns emptyList()

            assertThrows(SeatCountRequiredException::class.java) {
                service.createSubscription(
                    CreateSubscriptionRequest(customerId = 1L, planId = 1L, paymentMethod = "CREDIT_CARD", seatCount = null),
                )
            }
        }

        // CS-S6: Create non-per-seat with seat count (ignored)
        @Test
        fun `create non-per-seat with seat count ignores it`() {
            val plan = nonPerSeatPlan()
            every { planRepository.findByIdAndActiveTrue(2L) } returns plan
            every { subscriptionRepository.findByCustomerIdAndStatusIn(1L, any()) } returns emptyList()
            every { subscriptionRepository.save(any()) } answers { firstArg() }

            val result =
                service.createSubscription(
                    CreateSubscriptionRequest(customerId = 1L, planId = 2L, paymentMethod = "CREDIT_CARD", seatCount = 5),
                )

            assertNull(result.seatCount)
        }
    }

    @Nested
    inner class PlanChangeAddOnInteraction {
        // PC-1: Add-on stays on compatible plan change
        @Test
        fun `add-on remains active on compatible plan change`() {
            val proPlan = nonPerSeatPlan(id = 1L, tier = PlanTier.PROFESSIONAL)
            val entPlan =
                Plan(
                    id = 3L,
                    name = "Enterprise",
                    billingInterval = BillingInterval.MONTHLY,
                    basePrice = Money(BigDecimal("99.99"), Money.Currency.USD),
                    tier = PlanTier.ENTERPRISE,
                    features = setOf("feature1"),
                )
            val sub = sampleSubscription(plan = proPlan)
            val addOn =
                AddOn(
                    id = 10L,
                    name = "Support",
                    price = Money(BigDecimal("9.99"), Money.Currency.USD),
                    billingType = BillingType.FLAT,
                    compatibleTiers = setOf(PlanTier.PROFESSIONAL, PlanTier.ENTERPRISE),
                )
            val sa =
                SubscriptionAddOn(
                    id = 1L,
                    subscription = sub,
                    addOn = addOn,
                    quantity = 1,
                    status = SubscriptionAddOnStatus.ACTIVE,
                    attachedAt = fixedInstant.minus(Duration.ofDays(5)),
                )

            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { planRepository.findByIdAndActiveTrue(3L) } returns entPlan
            every { subscriptionAddOnRepository.findBySubscriptionIdAndStatus(1L, SubscriptionAddOnStatus.ACTIVE) } returns listOf(sa)
            every { paymentGateway.charge(any(), any(), any()) } returns PaymentResult(success = true, transactionId = "tx-1")
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionRepository.save(any()) } answers { firstArg() }

            val result = service.changePlan(1L, 3L)

            assertEquals(PlanTier.ENTERPRISE, result.plan.tier)
            // Add-on should NOT be detached
            verify(exactly = 0) { subscriptionAddOnRepository.save(any()) }
        }

        // PC-2: Add-on auto-detached on incompatible change
        @Test
        fun `add-on auto-detached on incompatible plan change`() {
            val proPlan = nonPerSeatPlan(id = 1L, tier = PlanTier.PROFESSIONAL)
            val starterPlan =
                Plan(
                    id = 4L,
                    name = "Starter",
                    billingInterval = BillingInterval.MONTHLY,
                    basePrice = Money(BigDecimal("19.99"), Money.Currency.USD),
                    tier = PlanTier.STARTER,
                    features = setOf("feature1"),
                )
            val sub = sampleSubscription(plan = proPlan)
            val addOn =
                AddOn(
                    id = 10L,
                    name = "Support",
                    price = Money(BigDecimal("9.99"), Money.Currency.USD),
                    billingType = BillingType.FLAT,
                    compatibleTiers = setOf(PlanTier.PROFESSIONAL, PlanTier.ENTERPRISE),
                )
            val sa =
                SubscriptionAddOn(
                    id = 1L,
                    subscription = sub,
                    addOn = addOn,
                    quantity = 1,
                    status = SubscriptionAddOnStatus.ACTIVE,
                    attachedAt = fixedInstant.minus(Duration.ofDays(5)),
                )

            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { planRepository.findByIdAndActiveTrue(4L) } returns starterPlan
            every { subscriptionAddOnRepository.findBySubscriptionIdAndStatus(1L, SubscriptionAddOnStatus.ACTIVE) } returns listOf(sa)
            every { subscriptionAddOnRepository.save(any()) } answers { firstArg() }
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionRepository.save(any()) } answers { firstArg() }

            service.changePlan(1L, 4L)

            // Add-on should be detached
            verify { subscriptionAddOnRepository.save(match { it.status == SubscriptionAddOnStatus.DETACHED }) }
        }

        // PC-4: PER_SEAT add-on detached on non-per-seat change
        @Test
        fun `PER_SEAT add-on detached when changing to non-per-seat plan`() {
            val perSeat = perSeatPlan(id = 1L)
            val nonPerSeat = nonPerSeatPlan(id = 2L)
            val sub = sampleSubscription(plan = perSeat, seatCount = 5)
            val addOn =
                AddOn(
                    id = 10L,
                    name = "Storage",
                    price = Money(BigDecimal("2.00"), Money.Currency.USD),
                    billingType = BillingType.PER_SEAT,
                    compatibleTiers = setOf(PlanTier.PROFESSIONAL),
                )
            val sa =
                SubscriptionAddOn(
                    id = 1L,
                    subscription = sub,
                    addOn = addOn,
                    quantity = 5,
                    status = SubscriptionAddOnStatus.ACTIVE,
                    attachedAt = fixedInstant.minus(Duration.ofDays(5)),
                )

            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { planRepository.findByIdAndActiveTrue(2L) } returns nonPerSeat
            every { subscriptionAddOnRepository.findBySubscriptionIdAndStatus(1L, SubscriptionAddOnStatus.ACTIVE) } returns listOf(sa)
            every { subscriptionAddOnRepository.save(any()) } answers { firstArg() }
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionRepository.save(any()) } answers { firstArg() }

            val result = service.changePlan(1L, 2L)

            assertNull(result.seatCount)
            verify { subscriptionAddOnRepository.save(match { it.status == SubscriptionAddOnStatus.DETACHED }) }
        }

        // PC-5: Non-per-seat to per-seat: seat count initialized
        @Test
        fun `seat count initialized to minSeats when changing to per-seat plan`() {
            val nonPerSeat = nonPerSeatPlan(id = 1L)
            val perSeat = perSeatPlan(id = 2L, minimumSeats = 3)
            val sub = sampleSubscription(plan = nonPerSeat)

            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { planRepository.findByIdAndActiveTrue(2L) } returns perSeat
            every { subscriptionAddOnRepository.findBySubscriptionIdAndStatus(1L, SubscriptionAddOnStatus.ACTIVE) } returns emptyList()
            every { paymentGateway.charge(any(), any(), any()) } returns PaymentResult(success = true, transactionId = "tx-1")
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionRepository.save(any()) } answers { firstArg() }

            val result = service.changePlan(1L, 2L)

            assertEquals(3, result.seatCount)
        }
    }

    @Nested
    inner class RenewalWithAddOns {
        private fun setupRenewalSub(
            plan: Plan = nonPerSeatPlan(),
            seatCount: Int? = null,
            accountCreditBalance: Money = Money.zero(plan.basePrice.currency),
        ): Subscription {
            val periodEnd = fixedInstant.minus(Duration.ofDays(1)) // Period already ended
            val periodStart = periodEnd.minus(Duration.ofDays(30))
            return sampleSubscription(
                plan = plan,
                seatCount = seatCount,
                accountCreditBalance = accountCreditBalance,
                currentPeriodStart = periodStart,
                currentPeriodEnd = periodEnd,
            )
        }

        // AR-1: Renewal with FLAT add-on
        @Test
        fun `renewal includes FLAT add-on charge`() {
            val sub = setupRenewalSub()
            val addOn =
                AddOn(
                    id = 10L,
                    name = "Support",
                    price = Money(BigDecimal("9.99"), Money.Currency.USD),
                    billingType = BillingType.FLAT,
                    compatibleTiers = setOf(PlanTier.PROFESSIONAL),
                )
            val sa =
                SubscriptionAddOn(
                    id = 1L,
                    subscription = sub,
                    addOn = addOn,
                    quantity = 1,
                    status = SubscriptionAddOnStatus.ACTIVE,
                    attachedAt = fixedInstant.minus(Duration.ofDays(20)),
                )

            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { subscriptionAddOnRepository.findBySubscriptionIdAndStatus(1L, SubscriptionAddOnStatus.ACTIVE) } returns listOf(sa)
            every {
                usageRecordRepository.findBySubscriptionIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThan(
                    any(),
                    any(),
                    any(),
                )
            } returns
                emptyList()
            every { discountRepository.findBySubscriptionId(1L) } returns null
            every { paymentGateway.charge(any(), any(), any()) } returns PaymentResult(success = true, transactionId = "tx-1")
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionRepository.save(any()) } answers { firstArg() }

            service.processRenewal(1L)

            val invoiceSlot = slot<Invoice>()
            verify { invoiceRepository.save(capture(invoiceSlot)) }
            val invoice = invoiceSlot.captured
            val addonLine = invoice.lineItems.find { it.type == LineItemType.ADDON_CHARGE }
            assertNotNull(addonLine)
            assertEquals(BigDecimal("9.99"), addonLine!!.amount.amount)
        }

        // AR-2: Renewal with PER_SEAT add-on
        @Test
        fun `renewal includes PER_SEAT add-on charge multiplied by seats`() {
            val plan = perSeatPlan(basePrice = Money(BigDecimal("10.00"), Money.Currency.USD))
            val periodEnd = fixedInstant.minus(Duration.ofDays(1))
            val periodStart = periodEnd.minus(Duration.ofDays(30))
            val sub =
                sampleSubscription(
                    plan = plan,
                    seatCount = 5,
                    currentPeriodStart = periodStart,
                    currentPeriodEnd = periodEnd,
                )
            val addOn =
                AddOn(
                    id = 10L,
                    name = "Storage",
                    price = Money(BigDecimal("2.00"), Money.Currency.USD),
                    billingType = BillingType.PER_SEAT,
                    compatibleTiers = setOf(PlanTier.PROFESSIONAL),
                )
            val sa =
                SubscriptionAddOn(
                    id = 1L,
                    subscription = sub,
                    addOn = addOn,
                    quantity = 5,
                    status = SubscriptionAddOnStatus.ACTIVE,
                    attachedAt = fixedInstant.minus(Duration.ofDays(20)),
                )

            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { subscriptionAddOnRepository.findBySubscriptionIdAndStatus(1L, SubscriptionAddOnStatus.ACTIVE) } returns listOf(sa)
            every {
                usageRecordRepository.findBySubscriptionIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThan(
                    any(),
                    any(),
                    any(),
                )
            } returns
                emptyList()
            every { discountRepository.findBySubscriptionId(1L) } returns null
            every { paymentGateway.charge(any(), any(), any()) } returns PaymentResult(success = true, transactionId = "tx-1")
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionRepository.save(any()) } answers { firstArg() }

            service.processRenewal(1L)

            val invoiceSlot = slot<Invoice>()
            verify { invoiceRepository.save(capture(invoiceSlot)) }
            val invoice = invoiceSlot.captured
            val addonLine = invoice.lineItems.find { it.type == LineItemType.ADDON_CHARGE }
            assertNotNull(addonLine)
            // 2.00 * 5 = 10.00
            assertEquals(BigDecimal("10.00"), addonLine!!.amount.amount)
        }

        // AR-5: Renewal with detached add-on (only active charged)
        @Test
        fun `renewal only charges active add-ons not detached`() {
            val sub = setupRenewalSub()
            val activeAddOn =
                AddOn(
                    id = 10L,
                    name = "Active",
                    price = Money(BigDecimal("9.99"), Money.Currency.USD),
                    billingType = BillingType.FLAT,
                    compatibleTiers = setOf(PlanTier.PROFESSIONAL),
                )
            val activeSA =
                SubscriptionAddOn(
                    id = 1L,
                    subscription = sub,
                    addOn = activeAddOn,
                    quantity = 1,
                    status = SubscriptionAddOnStatus.ACTIVE,
                    attachedAt = fixedInstant.minus(Duration.ofDays(20)),
                )
            // Detached add-on is not returned by findBySubscriptionIdAndStatus(ACTIVE)

            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { subscriptionAddOnRepository.findBySubscriptionIdAndStatus(1L, SubscriptionAddOnStatus.ACTIVE) } returns listOf(activeSA)
            every {
                usageRecordRepository.findBySubscriptionIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThan(
                    any(),
                    any(),
                    any(),
                )
            } returns
                emptyList()
            every { discountRepository.findBySubscriptionId(1L) } returns null
            every { paymentGateway.charge(any(), any(), any()) } returns PaymentResult(success = true, transactionId = "tx-1")
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionRepository.save(any()) } answers { firstArg() }

            service.processRenewal(1L)

            val invoiceSlot = slot<Invoice>()
            verify { invoiceRepository.save(capture(invoiceSlot)) }
            val addonLines = invoiceSlot.captured.lineItems.filter { it.type == LineItemType.ADDON_CHARGE }
            assertEquals(1, addonLines.size) // Only active add-on
        }

        // AR-6: Per-seat renewal total
        @Test
        fun `per-seat renewal uses base_price times seat_count`() {
            val plan = perSeatPlan(basePrice = Money(BigDecimal("10.00"), Money.Currency.USD))
            val periodEnd = fixedInstant.minus(Duration.ofDays(1))
            val periodStart = periodEnd.minus(Duration.ofDays(30))
            val sub =
                sampleSubscription(
                    plan = plan,
                    seatCount = 5,
                    currentPeriodStart = periodStart,
                    currentPeriodEnd = periodEnd,
                )

            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { subscriptionAddOnRepository.findBySubscriptionIdAndStatus(1L, SubscriptionAddOnStatus.ACTIVE) } returns emptyList()
            every {
                usageRecordRepository.findBySubscriptionIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThan(
                    any(),
                    any(),
                    any(),
                )
            } returns
                emptyList()
            every { discountRepository.findBySubscriptionId(1L) } returns null
            every { paymentGateway.charge(any(), any(), any()) } returns PaymentResult(success = true, transactionId = "tx-1")
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionRepository.save(any()) } answers { firstArg() }

            service.processRenewal(1L)

            val invoiceSlot = slot<Invoice>()
            verify { invoiceRepository.save(capture(invoiceSlot)) }
            val planLine = invoiceSlot.captured.lineItems.find { it.type == LineItemType.PLAN_CHARGE }
            assertNotNull(planLine)
            // 10.00 * 5 = 50.00
            assertEquals(BigDecimal("50.00"), planLine!!.amount.amount)
        }
    }

    @Nested
    inner class RenewalWithAccountCredit {
        // CR-1: Account credit applied to renewal
        @Test
        fun `account credit applied reduces charge amount`() {
            val plan = nonPerSeatPlan()
            val periodEnd = fixedInstant.minus(Duration.ofDays(1))
            val periodStart = periodEnd.minus(Duration.ofDays(30))
            val sub =
                sampleSubscription(
                    plan = plan,
                    accountCreditBalance = Money(BigDecimal("15.00"), Money.Currency.USD),
                    currentPeriodStart = periodStart,
                    currentPeriodEnd = periodEnd,
                )

            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { subscriptionAddOnRepository.findBySubscriptionIdAndStatus(1L, SubscriptionAddOnStatus.ACTIVE) } returns emptyList()
            every {
                usageRecordRepository.findBySubscriptionIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThan(
                    any(),
                    any(),
                    any(),
                )
            } returns
                emptyList()
            every { discountRepository.findBySubscriptionId(1L) } returns null
            every { paymentGateway.charge(any(), any(), any()) } returns PaymentResult(success = true, transactionId = "tx-1")
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionRepository.save(any()) } answers { firstArg() }

            service.processRenewal(1L)

            val invoiceSlot = slot<Invoice>()
            verify { invoiceRepository.save(capture(invoiceSlot)) }
            val invoice = invoiceSlot.captured
            // Credit line item
            val creditLine = invoice.lineItems.find { it.type == LineItemType.ACCOUNT_CREDIT }
            assertNotNull(creditLine)
            assertTrue(creditLine!!.amount.amount < BigDecimal.ZERO) // Negative amount
            // Total should be 49.99 - 15.00 = 34.99
            assertEquals(BigDecimal("34.99"), invoice.total.amount)
            // Gateway charged for 34.99
            verify {
                paymentGateway.charge(
                    match { it.amount == BigDecimal("34.99") },
                    any(),
                    any(),
                )
            }
        }

        // CR-2: Account credit exceeds renewal total
        @Test
        fun `account credit exceeding total results in zero charge`() {
            val plan = nonPerSeatPlan()
            val periodEnd = fixedInstant.minus(Duration.ofDays(1))
            val periodStart = periodEnd.minus(Duration.ofDays(30))
            val sub =
                sampleSubscription(
                    plan = plan,
                    accountCreditBalance = Money(BigDecimal("60.00"), Money.Currency.USD),
                    currentPeriodStart = periodStart,
                    currentPeriodEnd = periodEnd,
                )

            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { subscriptionAddOnRepository.findBySubscriptionIdAndStatus(1L, SubscriptionAddOnStatus.ACTIVE) } returns emptyList()
            every {
                usageRecordRepository.findBySubscriptionIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThan(
                    any(),
                    any(),
                    any(),
                )
            } returns
                emptyList()
            every { discountRepository.findBySubscriptionId(1L) } returns null
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionRepository.save(any()) } answers { firstArg() }

            service.processRenewal(1L)

            val invoiceSlot = slot<Invoice>()
            verify { invoiceRepository.save(capture(invoiceSlot)) }
            val invoice = invoiceSlot.captured
            // Total should be 0 since credit covers it
            assertTrue(invoice.total.amount.compareTo(BigDecimal.ZERO) == 0)
            // Gateway should NOT be charged
            verify(exactly = 0) { paymentGateway.charge(any(), any(), any()) }
            // Remaining balance: 60.00 - 49.99 = 10.01
            assertTrue(sub.accountCreditBalance.amount.compareTo(BigDecimal("10.01")) == 0)
        }

        // CR-3: Account credit covers exactly
        @Test
        fun `account credit covering exactly results in zero charge and zero balance`() {
            val plan = nonPerSeatPlan()
            val periodEnd = fixedInstant.minus(Duration.ofDays(1))
            val periodStart = periodEnd.minus(Duration.ofDays(30))
            val sub =
                sampleSubscription(
                    plan = plan,
                    accountCreditBalance = Money(BigDecimal("49.99"), Money.Currency.USD),
                    currentPeriodStart = periodStart,
                    currentPeriodEnd = periodEnd,
                )

            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { subscriptionAddOnRepository.findBySubscriptionIdAndStatus(1L, SubscriptionAddOnStatus.ACTIVE) } returns emptyList()
            every {
                usageRecordRepository.findBySubscriptionIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThan(
                    any(),
                    any(),
                    any(),
                )
            } returns
                emptyList()
            every { discountRepository.findBySubscriptionId(1L) } returns null
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionRepository.save(any()) } answers { firstArg() }

            service.processRenewal(1L)

            assertTrue(sub.accountCreditBalance.amount.compareTo(BigDecimal.ZERO) == 0)
        }

        // CR-5: No account credit (balance zero)
        @Test
        fun `no account credit line item when balance is zero`() {
            val plan = nonPerSeatPlan()
            val periodEnd = fixedInstant.minus(Duration.ofDays(1))
            val periodStart = periodEnd.minus(Duration.ofDays(30))
            val sub =
                sampleSubscription(
                    plan = plan,
                    accountCreditBalance = Money.zero(Money.Currency.USD),
                    currentPeriodStart = periodStart,
                    currentPeriodEnd = periodEnd,
                )

            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { subscriptionAddOnRepository.findBySubscriptionIdAndStatus(1L, SubscriptionAddOnStatus.ACTIVE) } returns emptyList()
            every {
                usageRecordRepository.findBySubscriptionIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThan(
                    any(),
                    any(),
                    any(),
                )
            } returns
                emptyList()
            every { discountRepository.findBySubscriptionId(1L) } returns null
            every { paymentGateway.charge(any(), any(), any()) } returns PaymentResult(success = true, transactionId = "tx-1")
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionRepository.save(any()) } answers { firstArg() }

            service.processRenewal(1L)

            val invoiceSlot = slot<Invoice>()
            verify { invoiceRepository.save(capture(invoiceSlot)) }
            val creditLine = invoiceSlot.captured.lineItems.find { it.type == LineItemType.ACCOUNT_CREDIT }
            assertNull(creditLine)
        }
    }
}
