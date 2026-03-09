package com.wakita181009.classic.service

import com.wakita181009.classic.exception.AddOnLimitReachedException
import com.wakita181009.classic.exception.AddOnNotFoundException
import com.wakita181009.classic.exception.CurrencyMismatchException
import com.wakita181009.classic.exception.DuplicateAddOnException
import com.wakita181009.classic.exception.InvalidStateTransitionException
import com.wakita181009.classic.exception.PaymentFailedException
import com.wakita181009.classic.exception.PerSeatAddOnOnNonPerSeatPlanException
import com.wakita181009.classic.exception.SubscriptionAddOnNotFoundException
import com.wakita181009.classic.exception.SubscriptionNotFoundException
import com.wakita181009.classic.exception.TierIncompatibilityException
import com.wakita181009.classic.model.AddOn
import com.wakita181009.classic.model.BillingInterval
import com.wakita181009.classic.model.BillingType
import com.wakita181009.classic.model.Money
import com.wakita181009.classic.model.Plan
import com.wakita181009.classic.model.PlanTier
import com.wakita181009.classic.model.Subscription
import com.wakita181009.classic.model.SubscriptionAddOn
import com.wakita181009.classic.model.SubscriptionAddOnStatus
import com.wakita181009.classic.model.SubscriptionStatus
import com.wakita181009.classic.repository.AddOnRepository
import com.wakita181009.classic.repository.InvoiceRepository
import com.wakita181009.classic.repository.SubscriptionAddOnRepository
import com.wakita181009.classic.repository.SubscriptionRepository
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
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
class AddOnServiceTest {
    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private val addOnRepository = mockk<AddOnRepository>()
    private val subscriptionAddOnRepository = mockk<SubscriptionAddOnRepository>()
    private val invoiceRepository = mockk<InvoiceRepository>()
    private val paymentGateway = mockk<PaymentGateway>()
    private val fixedInstant = Instant.parse("2025-01-15T00:00:00Z")
    private val clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    private lateinit var service: AddOnService

    @BeforeEach
    fun setUp() {
        service =
            AddOnService(
                subscriptionRepository = subscriptionRepository,
                addOnRepository = addOnRepository,
                subscriptionAddOnRepository = subscriptionAddOnRepository,
                invoiceRepository = invoiceRepository,
                paymentGateway = paymentGateway,
                clock = clock,
            )
    }

    private fun samplePlan(
        id: Long = 1L,
        tier: PlanTier = PlanTier.PROFESSIONAL,
        basePrice: Money = Money(BigDecimal("49.99"), Money.Currency.USD),
        perSeatPricing: Boolean = false,
        minimumSeats: Int = 1,
        maximumSeats: Int? = null,
    ) = Plan(
        id = id,
        name = "Professional",
        billingInterval = BillingInterval.MONTHLY,
        basePrice = basePrice,
        tier = tier,
        features = setOf("feature1"),
        perSeatPricing = perSeatPricing,
        minimumSeats = minimumSeats,
        maximumSeats = maximumSeats,
    )

    private fun sampleSubscription(
        id: Long = 1L,
        plan: Plan = samplePlan(),
        status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
        currentPeriodStart: Instant = Instant.parse("2025-01-01T00:00:00Z"),
        currentPeriodEnd: Instant = Instant.parse("2025-01-31T00:00:00Z"),
        seatCount: Int? = null,
    ) = Subscription(
        id = id,
        customerId = 1L,
        plan = plan,
        status = status,
        currentPeriodStart = currentPeriodStart,
        currentPeriodEnd = currentPeriodEnd,
        seatCount = seatCount,
        accountCreditBalance = Money.zero(plan.basePrice.currency),
    )

    private fun sampleAddOn(
        id: Long = 10L,
        price: Money = Money(BigDecimal("9.99"), Money.Currency.USD),
        billingType: BillingType = BillingType.FLAT,
        compatibleTiers: Set<PlanTier> = setOf(PlanTier.STARTER, PlanTier.PROFESSIONAL, PlanTier.ENTERPRISE),
    ) = AddOn(
        id = id,
        name = "Priority Support",
        price = price,
        billingType = billingType,
        compatibleTiers = compatibleTiers,
    )

    @Nested
    inner class AttachAddOn {
        // AO-H1: Attach FLAT add-on
        @Test
        fun `attach FLAT add-on with proration`() {
            val sub = sampleSubscription() // 15 of 30 days remaining (Jan 1-31, now = Jan 15)
            val addOn = sampleAddOn()

            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { addOnRepository.findByIdAndActiveTrue(10L) } returns addOn
            every { subscriptionAddOnRepository.findBySubscriptionIdAndAddOnIdAndStatus(1L, 10L, SubscriptionAddOnStatus.ACTIVE) } returns
                null
            every { subscriptionAddOnRepository.countBySubscriptionIdAndStatus(1L, SubscriptionAddOnStatus.ACTIVE) } returns 0
            every { paymentGateway.charge(any(), any(), any()) } returns PaymentResult(success = true, transactionId = "tx-1")
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionAddOnRepository.save(any()) } answers { firstArg() }

            val result = service.attachAddOn(1L, 10L)

            assertEquals(SubscriptionAddOnStatus.ACTIVE, result.status)
            assertEquals(1, result.quantity)
            assertNotNull(result.attachedAt)
        }

        // AO-H2: Attach PER_SEAT add-on
        @Test
        fun `attach PER_SEAT add-on uses seat count`() {
            val perSeatPlan = samplePlan(perSeatPricing = true, minimumSeats = 1, maximumSeats = 50)
            val sub = sampleSubscription(plan = perSeatPlan, seatCount = 5)
            val addOn = sampleAddOn(billingType = BillingType.PER_SEAT, price = Money(BigDecimal("2.00"), Money.Currency.USD))

            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { addOnRepository.findByIdAndActiveTrue(10L) } returns addOn
            every { subscriptionAddOnRepository.findBySubscriptionIdAndAddOnIdAndStatus(1L, 10L, SubscriptionAddOnStatus.ACTIVE) } returns
                null
            every { subscriptionAddOnRepository.countBySubscriptionIdAndStatus(1L, SubscriptionAddOnStatus.ACTIVE) } returns 0
            every { paymentGateway.charge(any(), any(), any()) } returns PaymentResult(success = true, transactionId = "tx-1")
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionAddOnRepository.save(any()) } answers { firstArg() }

            val result = service.attachAddOn(1L, 10L)

            assertEquals(5, result.quantity)
        }

        // AO-H3: Attach on first day of period
        @Test
        fun `attach on first day charges full price`() {
            val sub =
                sampleSubscription(
                    currentPeriodStart = fixedInstant,
                    currentPeriodEnd = fixedInstant.plus(Duration.ofDays(30)),
                )
            val addOn = sampleAddOn()

            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { addOnRepository.findByIdAndActiveTrue(10L) } returns addOn
            every { subscriptionAddOnRepository.findBySubscriptionIdAndAddOnIdAndStatus(1L, 10L, SubscriptionAddOnStatus.ACTIVE) } returns
                null
            every { subscriptionAddOnRepository.countBySubscriptionIdAndStatus(1L, SubscriptionAddOnStatus.ACTIVE) } returns 0
            every { paymentGateway.charge(any(), any(), any()) } returns PaymentResult(success = true, transactionId = "tx-1")
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionAddOnRepository.save(any()) } answers { firstArg() }

            val result = service.attachAddOn(1L, 10L)

            assertEquals(SubscriptionAddOnStatus.ACTIVE, result.status)
            // Full price since 30/30 days remaining
            verify(exactly = 1) { paymentGateway.charge(any(), any(), any()) }
        }

        // AO-H5: Attach 5th add-on (at limit)
        @Test
        fun `attach 5th add-on succeeds at limit`() {
            val sub = sampleSubscription()
            val addOn = sampleAddOn()

            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { addOnRepository.findByIdAndActiveTrue(10L) } returns addOn
            every { subscriptionAddOnRepository.findBySubscriptionIdAndAddOnIdAndStatus(1L, 10L, SubscriptionAddOnStatus.ACTIVE) } returns
                null
            every { subscriptionAddOnRepository.countBySubscriptionIdAndStatus(1L, SubscriptionAddOnStatus.ACTIVE) } returns 4
            every { paymentGateway.charge(any(), any(), any()) } returns PaymentResult(success = true, transactionId = "tx-1")
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionAddOnRepository.save(any()) } answers { firstArg() }

            val result = service.attachAddOn(1L, 10L)

            assertEquals(SubscriptionAddOnStatus.ACTIVE, result.status)
        }

        // AO-H6: Attach JPY add-on
        @Test
        fun `attach JPY add-on rounds to integer`() {
            val jpyPlan =
                samplePlan(
                    basePrice = Money(BigDecimal("5000"), Money.Currency.JPY),
                )
            // 17 of 30 days remaining
            val sub =
                sampleSubscription(
                    plan = jpyPlan,
                    currentPeriodStart = Instant.parse("2024-12-29T00:00:00Z"),
                    currentPeriodEnd = Instant.parse("2025-01-28T00:00:00Z"),
                )
            val addOn =
                sampleAddOn(
                    price = Money(BigDecimal("500"), Money.Currency.JPY),
                    compatibleTiers = setOf(PlanTier.PROFESSIONAL),
                )

            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { addOnRepository.findByIdAndActiveTrue(10L) } returns addOn
            every { subscriptionAddOnRepository.findBySubscriptionIdAndAddOnIdAndStatus(1L, 10L, SubscriptionAddOnStatus.ACTIVE) } returns
                null
            every { subscriptionAddOnRepository.countBySubscriptionIdAndStatus(1L, SubscriptionAddOnStatus.ACTIVE) } returns 0
            every { paymentGateway.charge(any(), any(), any()) } returns PaymentResult(success = true, transactionId = "tx-1")
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionAddOnRepository.save(any()) } answers { firstArg() }

            val result = service.attachAddOn(1L, 10L)

            assertEquals(SubscriptionAddOnStatus.ACTIVE, result.status)
        }
    }

    @Nested
    inner class AttachAddOnProration {
        // AO-P1: FLAT half-cycle
        @Test
        fun `FLAT half-cycle proration is correct`() {
            // USD(9.99) add-on, 15/30 remaining -> charge = USD(5.00) HALF_UP
            val sub = sampleSubscription() // Jan 1-31, now Jan 15 -> 16 days remaining actually
            val addOn = sampleAddOn(price = Money(BigDecimal("9.99"), Money.Currency.USD))

            // Use exact period for testing: 30 day period, 15 days remaining
            val exactSub =
                sampleSubscription(
                    currentPeriodStart = fixedInstant.minus(Duration.ofDays(15)),
                    currentPeriodEnd = fixedInstant.plus(Duration.ofDays(15)),
                )

            every { subscriptionRepository.findByIdWithPlan(1L) } returns exactSub
            every { addOnRepository.findByIdAndActiveTrue(10L) } returns addOn
            every { subscriptionAddOnRepository.findBySubscriptionIdAndAddOnIdAndStatus(1L, 10L, SubscriptionAddOnStatus.ACTIVE) } returns
                null
            every { subscriptionAddOnRepository.countBySubscriptionIdAndStatus(1L, SubscriptionAddOnStatus.ACTIVE) } returns 0
            every { paymentGateway.charge(any(), any(), any()) } returns PaymentResult(success = true, transactionId = "tx-1")
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionAddOnRepository.save(any()) } answers { firstArg() }

            service.attachAddOn(1L, 10L)

            // Verify the charge amount: 9.99 * 15/30 = 4.995 -> 5.00 HALF_UP
            verify {
                paymentGateway.charge(
                    match { it.amount == BigDecimal("5.00") && it.currency == Money.Currency.USD },
                    any(),
                    any(),
                )
            }
        }

        // AO-P2: FLAT one-third cycle
        @Test
        fun `FLAT one-third cycle proration is correct`() {
            // USD(9.99) add-on, 10/30 remaining -> charge = USD(3.33)
            val addOn = sampleAddOn(price = Money(BigDecimal("9.99"), Money.Currency.USD))
            val sub =
                sampleSubscription(
                    currentPeriodStart = fixedInstant.minus(Duration.ofDays(20)),
                    currentPeriodEnd = fixedInstant.plus(Duration.ofDays(10)),
                )

            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { addOnRepository.findByIdAndActiveTrue(10L) } returns addOn
            every { subscriptionAddOnRepository.findBySubscriptionIdAndAddOnIdAndStatus(1L, 10L, SubscriptionAddOnStatus.ACTIVE) } returns
                null
            every { subscriptionAddOnRepository.countBySubscriptionIdAndStatus(1L, SubscriptionAddOnStatus.ACTIVE) } returns 0
            every { paymentGateway.charge(any(), any(), any()) } returns PaymentResult(success = true, transactionId = "tx-1")
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionAddOnRepository.save(any()) } answers { firstArg() }

            service.attachAddOn(1L, 10L)

            verify {
                paymentGateway.charge(
                    match { it.amount == BigDecimal("3.33") },
                    any(),
                    any(),
                )
            }
        }

        // AO-P3: PER_SEAT 5 seats half-cycle
        @Test
        fun `PER_SEAT 5 seats half-cycle proration is correct`() {
            // USD(2.00) per-seat, 5 seats, 15/30 remaining -> charge = USD(5.00)
            val perSeatPlan = samplePlan(perSeatPricing = true, minimumSeats = 1, maximumSeats = 50)
            val sub =
                sampleSubscription(
                    plan = perSeatPlan,
                    seatCount = 5,
                    currentPeriodStart = fixedInstant.minus(Duration.ofDays(15)),
                    currentPeriodEnd = fixedInstant.plus(Duration.ofDays(15)),
                )
            val addOn = sampleAddOn(price = Money(BigDecimal("2.00"), Money.Currency.USD), billingType = BillingType.PER_SEAT)

            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { addOnRepository.findByIdAndActiveTrue(10L) } returns addOn
            every { subscriptionAddOnRepository.findBySubscriptionIdAndAddOnIdAndStatus(1L, 10L, SubscriptionAddOnStatus.ACTIVE) } returns
                null
            every { subscriptionAddOnRepository.countBySubscriptionIdAndStatus(1L, SubscriptionAddOnStatus.ACTIVE) } returns 0
            every { paymentGateway.charge(any(), any(), any()) } returns PaymentResult(success = true, transactionId = "tx-1")
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionAddOnRepository.save(any()) } answers { firstArg() }

            service.attachAddOn(1L, 10L)

            // 2.00 * 5 * 15/30 = 5.00
            verify {
                paymentGateway.charge(
                    match { it.amount == BigDecimal("5.00") },
                    any(),
                    any(),
                )
            }
        }

        // AO-P4: JPY rounding
        @Test
        fun `JPY rounding is correct`() {
            // JPY(500) add-on, 17/30 remaining -> charge = JPY(283)
            val jpyPlan = samplePlan(basePrice = Money(BigDecimal("5000"), Money.Currency.JPY))
            val sub =
                sampleSubscription(
                    plan = jpyPlan,
                    currentPeriodStart = fixedInstant.minus(Duration.ofDays(13)),
                    currentPeriodEnd = fixedInstant.plus(Duration.ofDays(17)),
                )
            val addOn =
                sampleAddOn(
                    price = Money(BigDecimal("500"), Money.Currency.JPY),
                    compatibleTiers = setOf(PlanTier.PROFESSIONAL),
                )

            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { addOnRepository.findByIdAndActiveTrue(10L) } returns addOn
            every { subscriptionAddOnRepository.findBySubscriptionIdAndAddOnIdAndStatus(1L, 10L, SubscriptionAddOnStatus.ACTIVE) } returns
                null
            every { subscriptionAddOnRepository.countBySubscriptionIdAndStatus(1L, SubscriptionAddOnStatus.ACTIVE) } returns 0
            every { paymentGateway.charge(any(), any(), any()) } returns PaymentResult(success = true, transactionId = "tx-1")
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionAddOnRepository.save(any()) } answers { firstArg() }

            service.attachAddOn(1L, 10L)

            // 500 * 17/30 = 283.33 -> 283 HALF_UP
            verify {
                paymentGateway.charge(
                    match { it.amount == BigDecimal("283") },
                    any(),
                    any(),
                )
            }
        }
    }

    @Nested
    inner class AttachAddOnErrors {
        // AO-E1: Subscription not found
        @Test
        fun `attach throws SubscriptionNotFoundException when subscription not found`() {
            every { subscriptionRepository.findByIdWithPlan(999L) } returns null
            assertThrows(SubscriptionNotFoundException::class.java) { service.attachAddOn(999L, 10L) }
        }

        // AO-E2: Subscription not Active (Paused)
        @Test
        fun `attach throws when subscription is Paused`() {
            val sub = sampleSubscription(status = SubscriptionStatus.PAUSED)
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            assertThrows(InvalidStateTransitionException::class.java) { service.attachAddOn(1L, 10L) }
        }

        // AO-E3: Subscription not Active (Trial)
        @Test
        fun `attach throws when subscription is Trial`() {
            val sub = sampleSubscription(status = SubscriptionStatus.TRIAL)
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            assertThrows(InvalidStateTransitionException::class.java) { service.attachAddOn(1L, 10L) }
        }

        // AO-E4: Add-on not found
        @Test
        fun `attach throws AddOnNotFoundException when add-on not found`() {
            val sub = sampleSubscription()
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { addOnRepository.findByIdAndActiveTrue(999L) } returns null
            assertThrows(AddOnNotFoundException::class.java) { service.attachAddOn(1L, 999L) }
        }

        // AO-E5: Add-on inactive
        @Test
        fun `attach throws AddOnNotFoundException when add-on is inactive`() {
            val sub = sampleSubscription()
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { addOnRepository.findByIdAndActiveTrue(10L) } returns null
            assertThrows(AddOnNotFoundException::class.java) { service.attachAddOn(1L, 10L) }
        }

        // AO-E6: Currency mismatch
        @Test
        fun `attach throws CurrencyMismatchException on currency mismatch`() {
            val sub = sampleSubscription()
            val eurAddOn =
                AddOn(
                    id = 10L,
                    name = "EUR Add-on",
                    price = Money(BigDecimal("9.99"), Money.Currency.EUR),
                    billingType = BillingType.FLAT,
                    compatibleTiers = setOf(PlanTier.PROFESSIONAL),
                )
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { addOnRepository.findByIdAndActiveTrue(10L) } returns eurAddOn
            assertThrows(CurrencyMismatchException::class.java) { service.attachAddOn(1L, 10L) }
        }

        // AO-E7: Tier incompatibility
        @Test
        fun `attach throws TierIncompatibilityException on tier mismatch`() {
            val starterPlan = samplePlan(tier = PlanTier.STARTER)
            val sub = sampleSubscription(plan = starterPlan)
            val proOnlyAddOn =
                sampleAddOn(compatibleTiers = setOf(PlanTier.PROFESSIONAL, PlanTier.ENTERPRISE))

            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { addOnRepository.findByIdAndActiveTrue(10L) } returns proOnlyAddOn
            assertThrows(TierIncompatibilityException::class.java) { service.attachAddOn(1L, 10L) }
        }

        // AO-E8: PER_SEAT on non-per-seat plan
        @Test
        fun `attach throws PerSeatAddOnOnNonPerSeatPlanException when not per-seat`() {
            val sub = sampleSubscription() // non-per-seat plan
            val perSeatAddOn = sampleAddOn(billingType = BillingType.PER_SEAT)

            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { addOnRepository.findByIdAndActiveTrue(10L) } returns perSeatAddOn
            assertThrows(PerSeatAddOnOnNonPerSeatPlanException::class.java) { service.attachAddOn(1L, 10L) }
        }

        // AO-E9: Duplicate add-on
        @Test
        fun `attach throws DuplicateAddOnException when already attached`() {
            val sub = sampleSubscription()
            val addOn = sampleAddOn()
            val existingSA =
                SubscriptionAddOn(
                    subscription = sub,
                    addOn = addOn,
                    quantity = 1,
                    status = SubscriptionAddOnStatus.ACTIVE,
                    attachedAt = fixedInstant,
                )

            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { addOnRepository.findByIdAndActiveTrue(10L) } returns addOn
            every { subscriptionAddOnRepository.findBySubscriptionIdAndAddOnIdAndStatus(1L, 10L, SubscriptionAddOnStatus.ACTIVE) } returns
                existingSA
            assertThrows(DuplicateAddOnException::class.java) { service.attachAddOn(1L, 10L) }
        }

        // AO-E10: Add-on limit reached
        @Test
        fun `attach throws AddOnLimitReachedException when at limit`() {
            val sub = sampleSubscription()
            val addOn = sampleAddOn()

            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { addOnRepository.findByIdAndActiveTrue(10L) } returns addOn
            every { subscriptionAddOnRepository.findBySubscriptionIdAndAddOnIdAndStatus(1L, 10L, SubscriptionAddOnStatus.ACTIVE) } returns
                null
            every { subscriptionAddOnRepository.countBySubscriptionIdAndStatus(1L, SubscriptionAddOnStatus.ACTIVE) } returns 5
            assertThrows(AddOnLimitReachedException::class.java) { service.attachAddOn(1L, 10L) }
        }

        // AO-E11: Payment fails
        @Test
        fun `attach throws PaymentFailedException when gateway fails`() {
            val sub = sampleSubscription()
            val addOn = sampleAddOn()

            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { addOnRepository.findByIdAndActiveTrue(10L) } returns addOn
            every { subscriptionAddOnRepository.findBySubscriptionIdAndAddOnIdAndStatus(1L, 10L, SubscriptionAddOnStatus.ACTIVE) } returns
                null
            every { subscriptionAddOnRepository.countBySubscriptionIdAndStatus(1L, SubscriptionAddOnStatus.ACTIVE) } returns 0
            every { paymentGateway.charge(any(), any(), any()) } returns PaymentResult(success = false, errorReason = "Declined")
            every { invoiceRepository.save(any()) } answers { firstArg() }

            assertThrows(PaymentFailedException::class.java) { service.attachAddOn(1L, 10L) }
            // Add-on should NOT be saved
            verify(exactly = 0) { subscriptionAddOnRepository.save(any()) }
        }
    }

    @Nested
    inner class DetachAddOn {
        // DA-H1: Detach FLAT add-on mid-cycle
        @Test
        fun `detach FLAT add-on credits account balance`() {
            val sub = sampleSubscription()
            val addOn = sampleAddOn()
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
            every { subscriptionAddOnRepository.findBySubscriptionIdAndAddOnIdAndStatus(1L, 10L, SubscriptionAddOnStatus.ACTIVE) } returns
                sa
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionRepository.save(any()) } answers { firstArg() }
            every { subscriptionAddOnRepository.save(any()) } answers { firstArg() }

            val result = service.detachAddOn(1L, 10L)

            assertEquals(SubscriptionAddOnStatus.DETACHED, result.status)
            assertNotNull(result.detachedAt)
        }

        // DA-H2: Detach PER_SEAT add-on
        @Test
        fun `detach PER_SEAT add-on credits for all seats`() {
            val perSeatPlan = samplePlan(perSeatPricing = true, minimumSeats = 1, maximumSeats = 50)
            val sub = sampleSubscription(plan = perSeatPlan, seatCount = 5)
            val addOn = sampleAddOn(billingType = BillingType.PER_SEAT, price = Money(BigDecimal("2.00"), Money.Currency.USD))
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
            every { subscriptionAddOnRepository.findBySubscriptionIdAndAddOnIdAndStatus(1L, 10L, SubscriptionAddOnStatus.ACTIVE) } returns
                sa
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionRepository.save(any()) } answers { firstArg() }
            every { subscriptionAddOnRepository.save(any()) } answers { firstArg() }

            val result = service.detachAddOn(1L, 10L)

            assertEquals(SubscriptionAddOnStatus.DETACHED, result.status)
        }

        // DA-H4: Detach while Paused
        @Test
        fun `detach while Paused uses frozen remaining days`() {
            val sub = sampleSubscription(status = SubscriptionStatus.PAUSED)
            sub.pausedAt = fixedInstant.minus(Duration.ofDays(5))
            val addOn = sampleAddOn()
            val sa =
                SubscriptionAddOn(
                    id = 1L,
                    subscription = sub,
                    addOn = addOn,
                    quantity = 1,
                    status = SubscriptionAddOnStatus.ACTIVE,
                    attachedAt = fixedInstant.minus(Duration.ofDays(10)),
                )

            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { subscriptionAddOnRepository.findBySubscriptionIdAndAddOnIdAndStatus(1L, 10L, SubscriptionAddOnStatus.ACTIVE) } returns
                sa
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionRepository.save(any()) } answers { firstArg() }
            every { subscriptionAddOnRepository.save(any()) } answers { firstArg() }

            val result = service.detachAddOn(1L, 10L)

            assertEquals(SubscriptionAddOnStatus.DETACHED, result.status)
        }
    }

    @Nested
    inner class DetachAddOnErrors {
        // DA-E1: Subscription not found
        @Test
        fun `detach throws SubscriptionNotFoundException when subscription not found`() {
            every { subscriptionRepository.findByIdWithPlan(999L) } returns null
            assertThrows(SubscriptionNotFoundException::class.java) { service.detachAddOn(999L, 10L) }
        }

        // DA-E2: Subscription in Trial
        @Test
        fun `detach throws when subscription is Trial`() {
            val sub = sampleSubscription(status = SubscriptionStatus.TRIAL)
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            assertThrows(InvalidStateTransitionException::class.java) { service.detachAddOn(1L, 10L) }
        }

        // DA-E3: Subscription Canceled
        @Test
        fun `detach throws when subscription is Canceled`() {
            val sub = sampleSubscription(status = SubscriptionStatus.CANCELED)
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            assertThrows(InvalidStateTransitionException::class.java) { service.detachAddOn(1L, 10L) }
        }

        // DA-E4: Add-on not attached
        @Test
        fun `detach throws SubscriptionAddOnNotFoundException when not attached`() {
            val sub = sampleSubscription()
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { subscriptionAddOnRepository.findBySubscriptionIdAndAddOnIdAndStatus(1L, 999L, SubscriptionAddOnStatus.ACTIVE) } returns
                null
            assertThrows(SubscriptionAddOnNotFoundException::class.java) { service.detachAddOn(1L, 999L) }
        }

        // DA-E5: Add-on already detached (returns null from findBySubscriptionIdAndAddOnIdAndStatus)
        @Test
        fun `detach throws when add-on already detached`() {
            val sub = sampleSubscription()
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { subscriptionAddOnRepository.findBySubscriptionIdAndAddOnIdAndStatus(1L, 10L, SubscriptionAddOnStatus.ACTIVE) } returns
                null
            assertThrows(SubscriptionAddOnNotFoundException::class.java) { service.detachAddOn(1L, 10L) }
        }
    }
}
