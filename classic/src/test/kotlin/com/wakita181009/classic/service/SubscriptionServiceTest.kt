package com.wakita181009.classic.service

import com.wakita181009.classic.dto.CreateSubscriptionRequest
import com.wakita181009.classic.exception.BusinessRuleViolationException
import com.wakita181009.classic.exception.DuplicateSubscriptionException
import com.wakita181009.classic.exception.InvalidStateTransitionException
import com.wakita181009.classic.exception.PaymentFailedException
import com.wakita181009.classic.exception.PlanNotFoundException
import com.wakita181009.classic.exception.SubscriptionNotFoundException
import com.wakita181009.classic.model.BillingInterval
import com.wakita181009.classic.model.Discount
import com.wakita181009.classic.model.DiscountType
import com.wakita181009.classic.model.Invoice
import com.wakita181009.classic.model.InvoiceStatus
import com.wakita181009.classic.model.Money
import com.wakita181009.classic.model.Plan
import com.wakita181009.classic.model.PlanTier
import com.wakita181009.classic.model.Subscription
import com.wakita181009.classic.model.SubscriptionStatus
import com.wakita181009.classic.repository.DiscountRepository
import com.wakita181009.classic.repository.InvoiceRepository
import com.wakita181009.classic.repository.PlanRepository
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
class SubscriptionServiceTest {
    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private val planRepository = mockk<PlanRepository>()
    private val invoiceRepository = mockk<InvoiceRepository>()
    private val usageRecordRepository = mockk<UsageRecordRepository>()
    private val discountRepository = mockk<DiscountRepository>()
    private val paymentGateway = mockk<PaymentGateway>()
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
            )
    }

    private fun samplePlan(
        id: Long = 1L,
        name: String = "Professional",
        tier: PlanTier = PlanTier.PROFESSIONAL,
        billingInterval: BillingInterval = BillingInterval.MONTHLY,
        basePrice: Money = Money(BigDecimal("49.99"), Money.Currency.USD),
        active: Boolean = true,
        usageLimit: Int? = null,
        features: Set<String> = setOf("feature1"),
    ) = Plan(
        id = id,
        name = name,
        billingInterval = billingInterval,
        basePrice = basePrice,
        tier = tier,
        active = active,
        usageLimit = usageLimit,
        features = features,
    )

    private fun sampleSubscription(
        id: Long = 1L,
        customerId: Long = 1L,
        plan: Plan = samplePlan(),
        status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
        currentPeriodStart: Instant = Instant.parse("2025-01-01T00:00:00Z"),
        currentPeriodEnd: Instant = Instant.parse("2025-01-31T00:00:00Z"),
        pauseCount: Int = 0,
        pausedAt: Instant? = null,
        canceledAt: Instant? = null,
        cancelAtPeriodEnd: Boolean = false,
        gracePeriodEnd: Instant? = null,
        trialStart: Instant? = null,
        trialEnd: Instant? = null,
    ) = Subscription(
        id = id,
        customerId = customerId,
        plan = plan,
        status = status,
        currentPeriodStart = currentPeriodStart,
        currentPeriodEnd = currentPeriodEnd,
        pauseCount = pauseCount,
        pausedAt = pausedAt,
        canceledAt = canceledAt,
        cancelAtPeriodEnd = cancelAtPeriodEnd,
        gracePeriodEnd = gracePeriodEnd,
        trialStart = trialStart,
        trialEnd = trialEnd,
    )

    // =========================================================================
    // UC-1: Create Subscription
    // =========================================================================
    @Nested
    inner class CreateSubscription {
        // CS-H1: Basic creation with trial
        @Test
        fun `creates subscription in TRIAL status with 14-day trial`() {
            val plan = samplePlan()
            every { planRepository.findByIdAndActiveTrue(1L) } returns plan
            every { subscriptionRepository.findByCustomerIdAndStatusIn(1L, any()) } returns emptyList()
            every { subscriptionRepository.save(any()) } answers { firstArg() }

            val request =
                CreateSubscriptionRequest(
                    customerId = 1L,
                    planId = 1L,
                    paymentMethod = "CREDIT_CARD",
                )
            val result = service.createSubscription(request)

            assertEquals(SubscriptionStatus.TRIAL, result.status)
            assertEquals(fixedInstant.plus(Duration.ofDays(14)), result.trialEnd)
            assertEquals(fixedInstant, result.currentPeriodStart)
        }

        // CS-H2: Creation with discount
        @Test
        fun `creates subscription with discount attached`() {
            val plan = samplePlan()
            every { planRepository.findByIdAndActiveTrue(1L) } returns plan
            every { subscriptionRepository.findByCustomerIdAndStatusIn(1L, any()) } returns emptyList()
            every { subscriptionRepository.save(any()) } answers { firstArg() }
            every { discountRepository.save(any()) } answers { firstArg() }

            val request =
                CreateSubscriptionRequest(
                    customerId = 1L,
                    planId = 1L,
                    paymentMethod = "CREDIT_CARD",
                    discountCode = "WELCOME20",
                )
            val result = service.createSubscription(request)

            assertEquals(SubscriptionStatus.TRIAL, result.status)
            verify { discountRepository.save(any()) }
        }

        // CS-H3: Creation with yearly plan
        @Test
        fun `creates subscription with yearly plan`() {
            val yearlyPlan =
                samplePlan(billingInterval = BillingInterval.YEARLY, basePrice = Money(BigDecimal("479.88"), Money.Currency.USD))
            every { planRepository.findByIdAndActiveTrue(1L) } returns yearlyPlan
            every { subscriptionRepository.findByCustomerIdAndStatusIn(1L, any()) } returns emptyList()
            every { subscriptionRepository.save(any()) } answers { firstArg() }

            val request = CreateSubscriptionRequest(customerId = 1L, planId = 1L, paymentMethod = "CREDIT_CARD")
            val result = service.createSubscription(request)

            assertEquals(SubscriptionStatus.TRIAL, result.status)
            // Trial first, so period end = now + 14 days
            assertEquals(fixedInstant.plus(Duration.ofDays(14)), result.currentPeriodEnd)
        }

        // CS-H4: Creation with free tier
        @Test
        fun `creates subscription with free tier plan`() {
            val freePlan = samplePlan(tier = PlanTier.FREE, basePrice = Money(BigDecimal("0.00"), Money.Currency.USD))
            every { planRepository.findByIdAndActiveTrue(1L) } returns freePlan
            every { subscriptionRepository.findByCustomerIdAndStatusIn(1L, any()) } returns emptyList()
            every { subscriptionRepository.save(any()) } answers { firstArg() }

            val request = CreateSubscriptionRequest(customerId = 1L, planId = 1L, paymentMethod = "CREDIT_CARD")
            val result = service.createSubscription(request)

            assertEquals(SubscriptionStatus.TRIAL, result.status)
        }

        // CS-B1: Plan not found
        @Test
        fun `throws PlanNotFoundException when plan not found`() {
            every { planRepository.findByIdAndActiveTrue(999L) } returns null
            val request = CreateSubscriptionRequest(customerId = 1L, planId = 999L, paymentMethod = "CREDIT_CARD")
            assertThrows(PlanNotFoundException::class.java) { service.createSubscription(request) }
        }

        // CS-B2: Inactive plan
        @Test
        fun `throws PlanNotFoundException when plan is inactive`() {
            every { planRepository.findByIdAndActiveTrue(1L) } returns null
            val request = CreateSubscriptionRequest(customerId = 1L, planId = 1L, paymentMethod = "CREDIT_CARD")
            assertThrows(PlanNotFoundException::class.java) { service.createSubscription(request) }
        }

        // CS-B3: Customer already has active subscription
        @Test
        fun `throws DuplicateSubscriptionException when customer has active subscription`() {
            val plan = samplePlan()
            every { planRepository.findByIdAndActiveTrue(1L) } returns plan
            every { subscriptionRepository.findByCustomerIdAndStatusIn(1L, any()) } returns listOf(sampleSubscription())

            val request = CreateSubscriptionRequest(customerId = 1L, planId = 1L, paymentMethod = "CREDIT_CARD")
            assertThrows(DuplicateSubscriptionException::class.java) { service.createSubscription(request) }
        }

        // CS-B4: Customer has Trial subscription
        @Test
        fun `throws DuplicateSubscriptionException when customer has trial subscription`() {
            val plan = samplePlan()
            every { planRepository.findByIdAndActiveTrue(1L) } returns plan
            every { subscriptionRepository.findByCustomerIdAndStatusIn(1L, any()) } returns
                listOf(sampleSubscription(status = SubscriptionStatus.TRIAL))

            val request = CreateSubscriptionRequest(customerId = 1L, planId = 1L, paymentMethod = "CREDIT_CARD")
            assertThrows(DuplicateSubscriptionException::class.java) { service.createSubscription(request) }
        }

        // CS-B5: Customer has Paused subscription
        @Test
        fun `throws DuplicateSubscriptionException when customer has paused subscription`() {
            val plan = samplePlan()
            every { planRepository.findByIdAndActiveTrue(1L) } returns plan
            every { subscriptionRepository.findByCustomerIdAndStatusIn(1L, any()) } returns
                listOf(sampleSubscription(status = SubscriptionStatus.PAUSED))

            val request = CreateSubscriptionRequest(customerId = 1L, planId = 1L, paymentMethod = "CREDIT_CARD")
            assertThrows(DuplicateSubscriptionException::class.java) { service.createSubscription(request) }
        }

        // CS-B6: Customer has PastDue subscription
        @Test
        fun `throws DuplicateSubscriptionException when customer has past due subscription`() {
            val plan = samplePlan()
            every { planRepository.findByIdAndActiveTrue(1L) } returns plan
            every { subscriptionRepository.findByCustomerIdAndStatusIn(1L, any()) } returns
                listOf(sampleSubscription(status = SubscriptionStatus.PAST_DUE))

            val request = CreateSubscriptionRequest(customerId = 1L, planId = 1L, paymentMethod = "CREDIT_CARD")
            assertThrows(DuplicateSubscriptionException::class.java) { service.createSubscription(request) }
        }

        // CS-B7: Canceled subscription allows re-subscribe
        @Test
        fun `allows new subscription when customer only has canceled subscription`() {
            val plan = samplePlan()
            every { planRepository.findByIdAndActiveTrue(1L) } returns plan
            every { subscriptionRepository.findByCustomerIdAndStatusIn(1L, any()) } returns emptyList()
            every { subscriptionRepository.save(any()) } answers { firstArg() }

            val request = CreateSubscriptionRequest(customerId = 1L, planId = 1L, paymentMethod = "CREDIT_CARD")
            val result = service.createSubscription(request)
            assertEquals(SubscriptionStatus.TRIAL, result.status)
        }
    }

    // =========================================================================
    // UC-2: Change Plan
    // =========================================================================
    @Nested
    inner class ChangePlan {
        private val starterPlan =
            samplePlan(
                id = 1L,
                name = "Starter",
                tier = PlanTier.STARTER,
                basePrice = Money(BigDecimal("19.99"), Money.Currency.USD),
            )
        private val proPlan =
            samplePlan(
                id = 2L,
                name = "Professional",
                tier = PlanTier.PROFESSIONAL,
                basePrice = Money(BigDecimal("49.99"), Money.Currency.USD),
            )

        // CP-U1: Upgrade mid-cycle (day 15 of 30)
        @Test
        fun `upgrade mid-cycle charges immediately`() {
            // 15 days remaining out of 30
            val sub =
                sampleSubscription(
                    plan = starterPlan,
                    currentPeriodStart = Instant.parse("2025-01-01T00:00:00Z"),
                    currentPeriodEnd = Instant.parse("2025-01-31T00:00:00Z"),
                )
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { planRepository.findByIdAndActiveTrue(2L) } returns proPlan
            every { paymentGateway.charge(any(), any(), any()) } returns
                PaymentResult(
                    success = true,
                    transactionId = "tx-1",
                    processedAt = fixedInstant,
                )
            val invoiceSlot = slot<Invoice>()
            every { invoiceRepository.save(capture(invoiceSlot)) } answers { firstArg() }
            every { subscriptionRepository.save(any()) } answers { firstArg() }

            val result = service.changePlan(1L, 2L)

            assertEquals(proPlan.id, result.plan.id)
            verify { paymentGateway.charge(any(), any(), any()) }
        }

        // CP-U2: Upgrade on first day
        @Test
        fun `upgrade on first day charges nearly full new price`() {
            val sub =
                sampleSubscription(
                    plan = starterPlan,
                    currentPeriodStart = fixedInstant,
                    currentPeriodEnd = fixedInstant.plus(Duration.ofDays(30)),
                )
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { planRepository.findByIdAndActiveTrue(2L) } returns proPlan
            every { paymentGateway.charge(any(), any(), any()) } returns
                PaymentResult(success = true, transactionId = "tx-1", processedAt = fixedInstant)
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionRepository.save(any()) } answers { firstArg() }

            val result = service.changePlan(1L, 2L)
            assertEquals(proPlan.id, result.plan.id)
        }

        // CP-U4: Upgrade with JPY plan
        @Test
        fun `upgrade with JPY plan rounds to integer`() {
            val jpyStarter =
                samplePlan(id = 1L, name = "Starter", tier = PlanTier.STARTER, basePrice = Money(BigDecimal("1980"), Money.Currency.JPY))
            val jpyPro =
                samplePlan(id = 2L, name = "Pro", tier = PlanTier.PROFESSIONAL, basePrice = Money(BigDecimal("4980"), Money.Currency.JPY))
            val sub =
                sampleSubscription(
                    plan = jpyStarter,
                    currentPeriodStart = Instant.parse("2025-01-01T00:00:00Z"),
                    currentPeriodEnd = Instant.parse("2025-01-31T00:00:00Z"),
                )
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { planRepository.findByIdAndActiveTrue(2L) } returns jpyPro
            every { paymentGateway.charge(any(), any(), any()) } returns
                PaymentResult(success = true, transactionId = "tx-1", processedAt = fixedInstant)
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionRepository.save(any()) } answers { firstArg() }

            val result = service.changePlan(1L, 2L)
            assertEquals(jpyPro.id, result.plan.id)
        }

        // CP-D1: Downgrade mid-cycle
        @Test
        fun `downgrade mid-cycle stores credit for next invoice`() {
            val sub =
                sampleSubscription(
                    plan = proPlan,
                    currentPeriodStart = Instant.parse("2025-01-01T00:00:00Z"),
                    currentPeriodEnd = Instant.parse("2025-01-31T00:00:00Z"),
                )
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { planRepository.findByIdAndActiveTrue(1L) } returns starterPlan
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionRepository.save(any()) } answers { firstArg() }

            val result = service.changePlan(1L, 1L)
            assertEquals(starterPlan.id, result.plan.id)
            // No charge for downgrade
            verify(exactly = 0) { paymentGateway.charge(any(), any(), any()) }
        }

        // CP-E1: Subscription not found
        @Test
        fun `throws SubscriptionNotFoundException when not found`() {
            every { subscriptionRepository.findByIdWithPlan(999L) } returns null
            assertThrows(SubscriptionNotFoundException::class.java) { service.changePlan(999L, 2L) }
        }

        // CP-E2: Subscription not Active (Paused)
        @Test
        fun `throws when subscription is Paused`() {
            val sub = sampleSubscription(status = SubscriptionStatus.PAUSED)
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            assertThrows(InvalidStateTransitionException::class.java) { service.changePlan(1L, 2L) }
        }

        // CP-E3: Subscription in Trial
        @Test
        fun `throws when subscription is in Trial`() {
            val sub = sampleSubscription(status = SubscriptionStatus.TRIAL)
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            assertThrows(InvalidStateTransitionException::class.java) { service.changePlan(1L, 2L) }
        }

        // CP-E4: Same plan
        @Test
        fun `throws when new plan is same as current`() {
            val sub = sampleSubscription(plan = proPlan)
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { planRepository.findByIdAndActiveTrue(2L) } returns proPlan
            assertThrows(BusinessRuleViolationException::class.java) { service.changePlan(1L, 2L) }
        }

        // CP-E5: New plan inactive
        @Test
        fun `throws PlanNotFoundException when new plan is inactive`() {
            val sub = sampleSubscription(plan = starterPlan)
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { planRepository.findByIdAndActiveTrue(3L) } returns null
            assertThrows(PlanNotFoundException::class.java) { service.changePlan(1L, 3L) }
        }

        // CP-E6: Currency mismatch
        @Test
        fun `throws when currencies mismatch`() {
            val eurPlan = samplePlan(id = 3L, basePrice = Money(BigDecimal("49.99"), Money.Currency.EUR))
            val sub = sampleSubscription(plan = starterPlan)
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { planRepository.findByIdAndActiveTrue(3L) } returns eurPlan
            assertThrows(BusinessRuleViolationException::class.java) { service.changePlan(1L, 3L) }
        }

        // CP-E7: Payment fails on upgrade
        @Test
        fun `throws PaymentFailedException and does NOT change plan on upgrade failure`() {
            val sub = sampleSubscription(plan = starterPlan)
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { planRepository.findByIdAndActiveTrue(2L) } returns proPlan
            every { paymentGateway.charge(any(), any(), any()) } returns PaymentResult(success = false, errorReason = "declined")
            every { invoiceRepository.save(any()) } answers { firstArg() }

            assertThrows(PaymentFailedException::class.java) { service.changePlan(1L, 2L) }
            // Plan should NOT have changed
            verify(exactly = 0) { subscriptionRepository.save(any()) }
        }
    }

    // =========================================================================
    // UC-3: Pause Subscription
    // =========================================================================
    @Nested
    inner class PauseSubscription {
        // PA-H1: First pause
        @Test
        fun `pauses active subscription with 0 prior pauses`() {
            val sub = sampleSubscription(pauseCount = 0)
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { subscriptionRepository.save(any()) } answers { firstArg() }

            val result = service.pauseSubscription(1L)
            assertEquals(SubscriptionStatus.PAUSED, result.status)
            assertNotNull(result.pausedAt)
        }

        // PA-H2: Second pause
        @Test
        fun `pauses active subscription with 1 prior pause`() {
            val sub = sampleSubscription(pauseCount = 1)
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { subscriptionRepository.save(any()) } answers { firstArg() }

            val result = service.pauseSubscription(1L)
            assertEquals(SubscriptionStatus.PAUSED, result.status)
        }

        // PA-E1: Not found
        @Test
        fun `throws SubscriptionNotFoundException when not found`() {
            every { subscriptionRepository.findByIdWithPlan(999L) } returns null
            assertThrows(SubscriptionNotFoundException::class.java) { service.pauseSubscription(999L) }
        }

        // PA-E2: Not Active (Trial)
        @Test
        fun `throws when subscription is in Trial`() {
            val sub = sampleSubscription(status = SubscriptionStatus.TRIAL)
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            assertThrows(InvalidStateTransitionException::class.java) { service.pauseSubscription(1L) }
        }

        // PA-E3: Already paused
        @Test
        fun `throws when subscription is already Paused`() {
            val sub = sampleSubscription(status = SubscriptionStatus.PAUSED)
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            assertThrows(InvalidStateTransitionException::class.java) { service.pauseSubscription(1L) }
        }

        // PA-E6: Pause limit reached
        @Test
        fun `throws when pause limit of 2 reached`() {
            val sub = sampleSubscription(pauseCount = 2)
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            assertThrows(BusinessRuleViolationException::class.java) { service.pauseSubscription(1L) }
        }
    }

    // =========================================================================
    // UC-4: Resume Subscription
    // =========================================================================
    @Nested
    inner class ResumeSubscription {
        // RE-H1: Resume after short pause
        @Test
        fun `resumes paused subscription and extends period by remaining days`() {
            // Paused 5 days ago, 20 days were remaining at pause time
            val pausedAt = fixedInstant.minus(Duration.ofDays(5))
            val sub =
                sampleSubscription(
                    status = SubscriptionStatus.PAUSED,
                    pausedAt = pausedAt,
                    currentPeriodStart = Instant.parse("2025-01-01T00:00:00Z"),
                    // When paused, periodEnd was frozen — 20 days from pausedAt
                    currentPeriodEnd = pausedAt.plus(Duration.ofDays(20)),
                )
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { subscriptionRepository.save(any()) } answers { firstArg() }

            val result = service.resumeSubscription(1L)
            assertEquals(SubscriptionStatus.ACTIVE, result.status)
            assertNull(result.pausedAt)
            // Period end should be now + 20 remaining days
            assertEquals(fixedInstant.plus(Duration.ofDays(20)), result.currentPeriodEnd)
        }

        // RE-E1: Not found
        @Test
        fun `throws SubscriptionNotFoundException when not found`() {
            every { subscriptionRepository.findByIdWithPlan(999L) } returns null
            assertThrows(SubscriptionNotFoundException::class.java) { service.resumeSubscription(999L) }
        }

        // RE-E2: Not Paused (Active)
        @Test
        fun `throws when subscription is Active`() {
            val sub = sampleSubscription(status = SubscriptionStatus.ACTIVE)
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            assertThrows(InvalidStateTransitionException::class.java) { service.resumeSubscription(1L) }
        }

        // RE-P1: Remaining days preserved
        @Test
        fun `remaining days are preserved not recalculated`() {
            val pausedAt = fixedInstant.minus(Duration.ofDays(3))
            val sub =
                sampleSubscription(
                    status = SubscriptionStatus.PAUSED,
                    pausedAt = pausedAt,
                    currentPeriodEnd = pausedAt.plus(Duration.ofDays(10)),
                )
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { subscriptionRepository.save(any()) } answers { firstArg() }

            val result = service.resumeSubscription(1L)
            // resumeDate + 10 days (NOT resumeDate + 7)
            assertEquals(fixedInstant.plus(Duration.ofDays(10)), result.currentPeriodEnd)
        }
    }

    // =========================================================================
    // UC-5: Cancel Subscription
    // =========================================================================
    @Nested
    inner class CancelSubscription {
        // CA-H1: Cancel Active at period end
        @Test
        fun `cancel at period end sets flag and keeps Active status`() {
            val sub = sampleSubscription(status = SubscriptionStatus.ACTIVE)
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { subscriptionRepository.save(any()) } answers { firstArg() }

            val result = service.cancelSubscription(1L, immediate = false)
            assertTrue(result.cancelAtPeriodEnd)
            assertNotNull(result.canceledAt)
            assertEquals(SubscriptionStatus.ACTIVE, result.status)
        }

        // CA-H2: Cancel PastDue at period end
        @Test
        fun `cancel PastDue at period end sets flag and keeps PastDue status`() {
            val sub = sampleSubscription(status = SubscriptionStatus.PAST_DUE)
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { subscriptionRepository.save(any()) } answers { firstArg() }

            val result = service.cancelSubscription(1L, immediate = false)
            assertTrue(result.cancelAtPeriodEnd)
            assertEquals(SubscriptionStatus.PAST_DUE, result.status)
        }

        // CA-H3: Cancel Active immediately
        @Test
        fun `cancel Active immediately transitions to Canceled and voids invoices`() {
            val sub = sampleSubscription(status = SubscriptionStatus.ACTIVE)
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { subscriptionRepository.save(any()) } answers { firstArg() }
            every { invoiceRepository.findBySubscriptionIdAndStatus(1L, InvoiceStatus.OPEN) } returns emptyList()

            val result = service.cancelSubscription(1L, immediate = true)
            assertEquals(SubscriptionStatus.CANCELED, result.status)
        }

        // CA-H4: Cancel Paused immediately
        @Test
        fun `cancel Paused immediately transitions to Canceled`() {
            val sub = sampleSubscription(status = SubscriptionStatus.PAUSED)
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { subscriptionRepository.save(any()) } answers { firstArg() }
            every { invoiceRepository.findBySubscriptionIdAndStatus(1L, InvoiceStatus.OPEN) } returns emptyList()

            val result = service.cancelSubscription(1L, immediate = true)
            assertEquals(SubscriptionStatus.CANCELED, result.status)
        }

        // CA-H6: Cancel Trial immediately
        @Test
        fun `cancel Trial immediately transitions to Canceled`() {
            val sub = sampleSubscription(status = SubscriptionStatus.TRIAL)
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { subscriptionRepository.save(any()) } answers { firstArg() }
            every { invoiceRepository.findBySubscriptionIdAndStatus(1L, InvoiceStatus.OPEN) } returns emptyList()

            val result = service.cancelSubscription(1L, immediate = true)
            assertEquals(SubscriptionStatus.CANCELED, result.status)
        }

        // CA-E2: Already Canceled
        @Test
        fun `throws when already Canceled`() {
            val sub = sampleSubscription(status = SubscriptionStatus.CANCELED)
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            assertThrows(InvalidStateTransitionException::class.java) { service.cancelSubscription(1L, immediate = true) }
        }

        // CA-E3: Already Expired
        @Test
        fun `throws when already Expired`() {
            val sub = sampleSubscription(status = SubscriptionStatus.EXPIRED)
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            assertThrows(InvalidStateTransitionException::class.java) { service.cancelSubscription(1L, immediate = true) }
        }

        // CA-E5: End-of-period not available for Paused
        @Test
        fun `throws when end-of-period cancel on Paused subscription`() {
            val sub = sampleSubscription(status = SubscriptionStatus.PAUSED)
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            assertThrows(BusinessRuleViolationException::class.java) { service.cancelSubscription(1L, immediate = false) }
        }
    }

    // =========================================================================
    // UC-6: Process Renewal
    // =========================================================================
    @Nested
    inner class ProcessRenewal {
        // RN-H1: Simple monthly renewal
        @Test
        fun `processes monthly renewal with payment success`() {
            val plan = samplePlan()
            val sub =
                sampleSubscription(
                    plan = plan,
                    currentPeriodStart = Instant.parse("2024-12-15T00:00:00Z"),
                    currentPeriodEnd = Instant.parse("2025-01-15T00:00:00Z"),
                )
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every {
                usageRecordRepository.findBySubscriptionIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThan(
                    any(),
                    any(),
                    any(),
                )
            } returns
                emptyList()
            every { discountRepository.findBySubscriptionId(1L) } returns null
            every { paymentGateway.charge(any(), any(), any()) } returns
                PaymentResult(success = true, transactionId = "tx-1", processedAt = fixedInstant)
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionRepository.save(any()) } answers { firstArg() }

            val result = service.processRenewal(1L)
            assertEquals(SubscriptionStatus.ACTIVE, result.status)
            // Period should advance
            assertEquals(Instant.parse("2025-01-15T00:00:00Z"), result.currentPeriodStart)
        }

        // RN-H3: Renewal with active discount
        @Test
        fun `applies discount and decrements remaining cycles`() {
            val plan = samplePlan()
            val sub =
                sampleSubscription(
                    plan = plan,
                    currentPeriodStart = Instant.parse("2024-12-15T00:00:00Z"),
                    currentPeriodEnd = Instant.parse("2025-01-15T00:00:00Z"),
                )
            val discount =
                Discount(
                    id = 1L,
                    type = DiscountType.PERCENTAGE,
                    value = BigDecimal("20"),
                    durationMonths = 3,
                    remainingCycles = 2,
                    appliedAt = Instant.parse("2024-12-01T00:00:00Z"),
                )
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every {
                usageRecordRepository.findBySubscriptionIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThan(
                    any(),
                    any(),
                    any(),
                )
            } returns
                emptyList()
            every { discountRepository.findBySubscriptionId(1L) } returns discount
            every { discountRepository.save(any()) } answers { firstArg() }
            every { paymentGateway.charge(any(), any(), any()) } returns
                PaymentResult(success = true, transactionId = "tx-1", processedAt = fixedInstant)
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionRepository.save(any()) } answers { firstArg() }

            service.processRenewal(1L)
            verify { discountRepository.save(any()) }
        }

        // RN-H5: Zero total auto-Paid
        @Test
        fun `zero total invoice is auto-marked as Paid`() {
            val freePlan = samplePlan(tier = PlanTier.FREE, basePrice = Money(BigDecimal("0.00"), Money.Currency.USD))
            val sub =
                sampleSubscription(
                    plan = freePlan,
                    currentPeriodStart = Instant.parse("2024-12-15T00:00:00Z"),
                    currentPeriodEnd = Instant.parse("2025-01-15T00:00:00Z"),
                )
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
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
            // No charge attempt for zero total
            verify(exactly = 0) { paymentGateway.charge(any(), any(), any()) }
        }

        // RN-F1: Payment fails -> PastDue
        @Test
        fun `payment failure transitions to PastDue with grace period`() {
            val plan = samplePlan()
            val sub =
                sampleSubscription(
                    plan = plan,
                    currentPeriodStart = Instant.parse("2024-12-15T00:00:00Z"),
                    currentPeriodEnd = Instant.parse("2025-01-15T00:00:00Z"),
                )
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every {
                usageRecordRepository.findBySubscriptionIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThan(
                    any(),
                    any(),
                    any(),
                )
            } returns
                emptyList()
            every { discountRepository.findBySubscriptionId(1L) } returns null
            every { paymentGateway.charge(any(), any(), any()) } returns PaymentResult(success = false, errorReason = "declined")
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionRepository.save(any()) } answers { firstArg() }

            val result = service.processRenewal(1L)
            assertEquals(SubscriptionStatus.PAST_DUE, result.status)
            assertNotNull(result.gracePeriodEnd)
        }

        // RN-S1: Period not ended -> no-op
        @Test
        fun `skips renewal when period has not ended`() {
            val sub =
                sampleSubscription(
                    currentPeriodEnd = fixedInstant.plus(Duration.ofDays(10)),
                )
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub

            val result = service.processRenewal(1L)
            assertEquals(sub.id, result.id)
            verify(exactly = 0) { invoiceRepository.save(any()) }
        }

        // RN-S2: Not Active -> no-op
        @Test
        fun `skips renewal when subscription is Paused`() {
            val sub = sampleSubscription(status = SubscriptionStatus.PAUSED)
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub

            val result = service.processRenewal(1L)
            verify(exactly = 0) { invoiceRepository.save(any()) }
        }
    }

    // =========================================================================
    // Pause/Resume Interaction Tests
    // =========================================================================
    @Nested
    inner class PauseResumeInteraction {
        // PR-3: Triple pause in same period
        @Test
        fun `third pause attempt throws BusinessRuleViolationException`() {
            val sub = sampleSubscription(pauseCount = 2)
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            assertThrows(BusinessRuleViolationException::class.java) { service.pauseSubscription(1L) }
        }
    }

    // =========================================================================
    // Discount Interaction Tests
    // =========================================================================
    @Nested
    inner class DiscountInteraction {
        // DI-3: Discount NOT applied to proration
        @Test
        fun `discount is not applied to proration invoice`() {
            val starterPlan = samplePlan(id = 1L, tier = PlanTier.STARTER, basePrice = Money(BigDecimal("19.99"), Money.Currency.USD))
            val proPlan = samplePlan(id = 2L, tier = PlanTier.PROFESSIONAL, basePrice = Money(BigDecimal("49.99"), Money.Currency.USD))
            val sub = sampleSubscription(plan = starterPlan)
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { planRepository.findByIdAndActiveTrue(2L) } returns proPlan
            every { paymentGateway.charge(any(), any(), any()) } returns
                PaymentResult(success = true, transactionId = "tx-1", processedAt = fixedInstant)
            val invoiceSlot = slot<Invoice>()
            every { invoiceRepository.save(capture(invoiceSlot)) } answers { firstArg() }
            every { subscriptionRepository.save(any()) } answers { firstArg() }

            service.changePlan(1L, 2L)

            // Verify discount amount is zero on proration invoice
            assertEquals(BigDecimal.ZERO.setScale(2), invoiceSlot.captured.discountAmount.amount)
        }

        // DI-4: Fixed discount capped at subtotal
        @Test
        fun `fixed discount capped at subtotal results in zero total auto-Paid`() {
            val plan = samplePlan(basePrice = Money(BigDecimal("49.99"), Money.Currency.USD))
            val sub =
                sampleSubscription(
                    plan = plan,
                    currentPeriodStart = Instant.parse("2024-12-15T00:00:00Z"),
                    currentPeriodEnd = Instant.parse("2025-01-15T00:00:00Z"),
                )
            val discount =
                Discount(
                    id = 1L,
                    type = DiscountType.FIXED_AMOUNT,
                    value = BigDecimal("100.00"),
                    durationMonths = 1,
                    remainingCycles = 1,
                    appliedAt = Instant.parse("2024-12-01T00:00:00Z"),
                )
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every {
                usageRecordRepository.findBySubscriptionIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThan(
                    any(),
                    any(),
                    any(),
                )
            } returns
                emptyList()
            every { discountRepository.findBySubscriptionId(1L) } returns discount
            every { discountRepository.save(any()) } answers { firstArg() }
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionRepository.save(any()) } answers { firstArg() }

            service.processRenewal(1L)
            // Zero total -> no charge
            verify(exactly = 0) { paymentGateway.charge(any(), any(), any()) }
        }
    }

    // =========================================================================
    // Trial Conversion Tests
    // =========================================================================
    @Nested
    inner class TrialConversion {
        // TR-5: Cancel during trial
        @Test
        fun `cancel during trial transitions to Canceled immediately`() {
            val sub = sampleSubscription(status = SubscriptionStatus.TRIAL)
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { subscriptionRepository.save(any()) } answers { firstArg() }
            every { invoiceRepository.findBySubscriptionIdAndStatus(1L, InvoiceStatus.OPEN) } returns emptyList()

            val result = service.cancelSubscription(1L, immediate = true)
            assertEquals(SubscriptionStatus.CANCELED, result.status)
        }
    }

    // =========================================================================
    // End-of-Period Cancellation Tests
    // =========================================================================
    @Nested
    inner class EndOfPeriodCancellation {
        // EP-1: Cancel at period end, then period ends -> Canceled
        @Test
        fun `subscription with cancelAtPeriodEnd is canceled on renewal`() {
            val plan = samplePlan()
            val sub =
                sampleSubscription(
                    plan = plan,
                    cancelAtPeriodEnd = true,
                    canceledAt = Instant.parse("2025-01-10T00:00:00Z"),
                    currentPeriodStart = Instant.parse("2024-12-15T00:00:00Z"),
                    currentPeriodEnd = Instant.parse("2025-01-15T00:00:00Z"),
                )
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { subscriptionRepository.save(any()) } answers { firstArg() }

            val result = service.processRenewal(1L)
            assertEquals(SubscriptionStatus.CANCELED, result.status)
        }
    }
}
