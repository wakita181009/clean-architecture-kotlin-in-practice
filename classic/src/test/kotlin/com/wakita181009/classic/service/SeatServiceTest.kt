package com.wakita181009.classic.service

import com.wakita181009.classic.exception.InvalidStateTransitionException
import com.wakita181009.classic.exception.NotPerSeatPlanException
import com.wakita181009.classic.exception.PaymentFailedException
import com.wakita181009.classic.exception.SameSeatCountException
import com.wakita181009.classic.exception.SeatCountOutOfRangeException
import com.wakita181009.classic.exception.SubscriptionNotFoundException
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
import com.wakita181009.classic.repository.InvoiceRepository
import com.wakita181009.classic.repository.SubscriptionAddOnRepository
import com.wakita181009.classic.repository.SubscriptionRepository
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
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
class SeatServiceTest {
    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private val subscriptionAddOnRepository = mockk<SubscriptionAddOnRepository>()
    private val invoiceRepository = mockk<InvoiceRepository>()
    private val paymentGateway = mockk<PaymentGateway>()
    private val fixedInstant = Instant.parse("2025-01-15T00:00:00Z")
    private val clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    private lateinit var service: SeatService

    @BeforeEach
    fun setUp() {
        service =
            SeatService(
                subscriptionRepository = subscriptionRepository,
                subscriptionAddOnRepository = subscriptionAddOnRepository,
                invoiceRepository = invoiceRepository,
                paymentGateway = paymentGateway,
                clock = clock,
            )
    }

    private fun perSeatPlan(
        id: Long = 1L,
        basePrice: Money = Money(BigDecimal("10.00"), Money.Currency.USD),
        minimumSeats: Int = 1,
        maximumSeats: Int? = 100,
    ) = Plan(
        id = id,
        name = "Team",
        billingInterval = BillingInterval.MONTHLY,
        basePrice = basePrice,
        tier = PlanTier.PROFESSIONAL,
        features = setOf("feature1"),
        perSeatPricing = true,
        minimumSeats = minimumSeats,
        maximumSeats = maximumSeats,
    )

    private fun sampleSubscription(
        plan: Plan = perSeatPlan(),
        seatCount: Int = 5,
        status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
        currentPeriodStart: Instant = fixedInstant.minus(Duration.ofDays(15)),
        currentPeriodEnd: Instant = fixedInstant.plus(Duration.ofDays(15)),
    ) = Subscription(
        id = 1L,
        customerId = 1L,
        plan = plan,
        status = status,
        seatCount = seatCount,
        currentPeriodStart = currentPeriodStart,
        currentPeriodEnd = currentPeriodEnd,
        accountCreditBalance = Money.zero(plan.basePrice.currency),
    )

    private fun perSeatAddOn(
        sub: Subscription,
        price: Money = Money(BigDecimal("2.00"), Money.Currency.USD),
        quantity: Int = sub.seatCount ?: 5,
    ): SubscriptionAddOn {
        val addOn =
            AddOn(
                id = 20L,
                name = "Extra Storage",
                price = price,
                billingType = BillingType.PER_SEAT,
                compatibleTiers = setOf(PlanTier.PROFESSIONAL),
            )
        return SubscriptionAddOn(
            id = 1L,
            subscription = sub,
            addOn = addOn,
            quantity = quantity,
            status = SubscriptionAddOnStatus.ACTIVE,
            attachedAt = fixedInstant.minus(Duration.ofDays(10)),
        )
    }

    private fun flatAddOn(sub: Subscription): SubscriptionAddOn {
        val addOn =
            AddOn(
                id = 30L,
                name = "Support",
                price = Money(BigDecimal("9.99"), Money.Currency.USD),
                billingType = BillingType.FLAT,
                compatibleTiers = setOf(PlanTier.PROFESSIONAL),
            )
        return SubscriptionAddOn(
            id = 2L,
            subscription = sub,
            addOn = addOn,
            quantity = 1,
            status = SubscriptionAddOnStatus.ACTIVE,
            attachedAt = fixedInstant.minus(Duration.ofDays(10)),
        )
    }

    @Nested
    inner class SeatIncrease {
        // ST-U1: Increase by 1 seat mid-cycle
        @Test
        fun `increase by 1 seat charges prorated amount`() {
            val sub = sampleSubscription() // 5 seats, 15/30 days remaining
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { subscriptionAddOnRepository.findBySubscriptionIdAndStatus(1L, SubscriptionAddOnStatus.ACTIVE) } returns emptyList()
            every { paymentGateway.charge(any(), any(), any()) } returns PaymentResult(success = true, transactionId = "tx-1")
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionRepository.save(any()) } answers { firstArg() }

            val result = service.updateSeatCount(1L, 6)

            assertEquals(6, result.seatCount)
            // 10.00 * 1 * 15/30 = 5.00
            verify {
                paymentGateway.charge(
                    match { it.amount == BigDecimal("5.00") },
                    any(),
                    any(),
                )
            }
        }

        // ST-U2: Increase by 5 seats
        @Test
        fun `increase by 5 seats charges prorated amount`() {
            val sub = sampleSubscription()
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { subscriptionAddOnRepository.findBySubscriptionIdAndStatus(1L, SubscriptionAddOnStatus.ACTIVE) } returns emptyList()
            every { paymentGateway.charge(any(), any(), any()) } returns PaymentResult(success = true, transactionId = "tx-1")
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionRepository.save(any()) } answers { firstArg() }

            val result = service.updateSeatCount(1L, 10)

            assertEquals(10, result.seatCount)
            // 10.00 * 5 * 15/30 = 25.00
            verify {
                paymentGateway.charge(
                    match { it.amount == BigDecimal("25.00") },
                    any(),
                    any(),
                )
            }
        }

        // ST-U3: Increase to maximum
        @Test
        fun `increase to maximum succeeds`() {
            val plan = perSeatPlan(maximumSeats = 10)
            val sub = sampleSubscription(plan = plan, seatCount = 5)
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { subscriptionAddOnRepository.findBySubscriptionIdAndStatus(1L, SubscriptionAddOnStatus.ACTIVE) } returns emptyList()
            every { paymentGateway.charge(any(), any(), any()) } returns PaymentResult(success = true, transactionId = "tx-1")
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionRepository.save(any()) } answers { firstArg() }

            val result = service.updateSeatCount(1L, 10)

            assertEquals(10, result.seatCount)
        }

        // ST-U4: Increase with PER_SEAT add-on
        @Test
        fun `increase with PER_SEAT add-on charges both prorations`() {
            val sub = sampleSubscription()
            val sa = perSeatAddOn(sub)
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { subscriptionAddOnRepository.findBySubscriptionIdAndStatus(1L, SubscriptionAddOnStatus.ACTIVE) } returns listOf(sa)
            every { paymentGateway.charge(any(), any(), any()) } returns PaymentResult(success = true, transactionId = "tx-1")
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionRepository.save(any()) } answers { firstArg() }
            every { subscriptionAddOnRepository.save(any()) } answers { firstArg() }

            val result = service.updateSeatCount(1L, 7)

            assertEquals(7, result.seatCount)
            // Seat proration: 10.00 * 2 * 15/30 = 10.00
            // Add-on proration: 2.00 * 2 * 15/30 = 2.00
            // Total: 12.00
            verify {
                paymentGateway.charge(
                    match { it.amount == BigDecimal("12.00") },
                    any(),
                    any(),
                )
            }
            // Add-on quantity updated
            verify { subscriptionAddOnRepository.save(match { it.quantity == 7 }) }
        }
    }

    @Nested
    inner class SeatDecrease {
        // ST-D1: Decrease by 1 seat
        @Test
        fun `decrease by 1 seat credits account balance`() {
            val sub = sampleSubscription()
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { subscriptionAddOnRepository.findBySubscriptionIdAndStatus(1L, SubscriptionAddOnStatus.ACTIVE) } returns emptyList()
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionRepository.save(any()) } answers { firstArg() }

            val result = service.updateSeatCount(1L, 4)

            assertEquals(4, result.seatCount)
            // Credit: 10.00 * 1 * 15/30 = 5.00
            assertTrue(result.accountCreditBalance.amount.compareTo(BigDecimal("5.00")) == 0)
        }

        // ST-D2: Decrease to minimum
        @Test
        fun `decrease to minimum succeeds`() {
            val sub = sampleSubscription()
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { subscriptionAddOnRepository.findBySubscriptionIdAndStatus(1L, SubscriptionAddOnStatus.ACTIVE) } returns emptyList()
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionRepository.save(any()) } answers { firstArg() }

            val result = service.updateSeatCount(1L, 1)

            assertEquals(1, result.seatCount)
            // Credit for 4 seats: 10.00 * 4 * 15/30 = 20.00
            assertTrue(result.accountCreditBalance.amount.compareTo(BigDecimal("20.00")) == 0)
        }

        // ST-D3: Decrease with PER_SEAT add-on
        @Test
        fun `decrease with PER_SEAT add-on credits seat and add-on prorations`() {
            val sub = sampleSubscription()
            val sa = perSeatAddOn(sub)
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { subscriptionAddOnRepository.findBySubscriptionIdAndStatus(1L, SubscriptionAddOnStatus.ACTIVE) } returns listOf(sa)
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionRepository.save(any()) } answers { firstArg() }
            every { subscriptionAddOnRepository.save(any()) } answers { firstArg() }

            val result = service.updateSeatCount(1L, 3)

            assertEquals(3, result.seatCount)
            // Seat credit: 10.00 * 2 * 15/30 = 10.00
            // Add-on credit: 2.00 * 2 * 15/30 = 2.00
            // Total credit: 12.00
            assertTrue(result.accountCreditBalance.amount.compareTo(BigDecimal("12.00")) == 0)
            verify { subscriptionAddOnRepository.save(match { it.quantity == 3 }) }
        }
    }

    @Nested
    inner class SeatProration {
        // ST-P1: One-third cycle remaining
        @Test
        fun `one-third cycle remaining proration is correct`() {
            val sub =
                sampleSubscription(
                    currentPeriodStart = fixedInstant.minus(Duration.ofDays(20)),
                    currentPeriodEnd = fixedInstant.plus(Duration.ofDays(10)),
                    seatCount = 5,
                )
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { subscriptionAddOnRepository.findBySubscriptionIdAndStatus(1L, SubscriptionAddOnStatus.ACTIVE) } returns emptyList()
            every { paymentGateway.charge(any(), any(), any()) } returns PaymentResult(success = true, transactionId = "tx-1")
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionRepository.save(any()) } answers { firstArg() }

            service.updateSeatCount(1L, 8)

            // 10.00 * 3 * 10/30 = 10.00
            verify {
                paymentGateway.charge(
                    match { it.amount == BigDecimal("10.00") },
                    any(),
                    any(),
                )
            }
        }

        // ST-P2: HALF_UP rounding
        @Test
        fun `HALF_UP rounding is correct`() {
            val sub =
                sampleSubscription(
                    currentPeriodStart = fixedInstant.minus(Duration.ofDays(13)),
                    currentPeriodEnd = fixedInstant.plus(Duration.ofDays(17)),
                    seatCount = 5,
                )
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { subscriptionAddOnRepository.findBySubscriptionIdAndStatus(1L, SubscriptionAddOnStatus.ACTIVE) } returns emptyList()
            every { paymentGateway.charge(any(), any(), any()) } returns PaymentResult(success = true, transactionId = "tx-1")
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionRepository.save(any()) } answers { firstArg() }

            service.updateSeatCount(1L, 6)

            // 10.00 * 1 * 17/30 = 5.6667 -> 5.67 HALF_UP
            verify {
                paymentGateway.charge(
                    match { it.amount == BigDecimal("5.67") },
                    any(),
                    any(),
                )
            }
        }

        // ST-P3: JPY rounding
        @Test
        fun `JPY rounding is correct`() {
            val jpyPlan =
                perSeatPlan(
                    basePrice = Money(BigDecimal("1000"), Money.Currency.JPY),
                )
            val sub =
                sampleSubscription(
                    plan = jpyPlan,
                    seatCount = 5,
                    currentPeriodStart = fixedInstant.minus(Duration.ofDays(13)),
                    currentPeriodEnd = fixedInstant.plus(Duration.ofDays(17)),
                )
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { subscriptionAddOnRepository.findBySubscriptionIdAndStatus(1L, SubscriptionAddOnStatus.ACTIVE) } returns emptyList()
            every { paymentGateway.charge(any(), any(), any()) } returns PaymentResult(success = true, transactionId = "tx-1")
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionRepository.save(any()) } answers { firstArg() }

            service.updateSeatCount(1L, 7)

            // 1000 * 2 * 17/30 = 1133.33 -> 1133 HALF_UP
            verify {
                paymentGateway.charge(
                    match { it.amount == BigDecimal("1133") },
                    any(),
                    any(),
                )
            }
        }
    }

    @Nested
    inner class SeatErrors {
        // ST-E1: Subscription not found
        @Test
        fun `throws SubscriptionNotFoundException when not found`() {
            every { subscriptionRepository.findByIdWithPlan(999L) } returns null
            assertThrows(SubscriptionNotFoundException::class.java) { service.updateSeatCount(999L, 5) }
        }

        // ST-E2: Subscription not Active
        @Test
        fun `throws when subscription is Paused`() {
            val sub = sampleSubscription(status = SubscriptionStatus.PAUSED)
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            assertThrows(InvalidStateTransitionException::class.java) { service.updateSeatCount(1L, 6) }
        }

        // ST-E3: Not a per-seat plan
        @Test
        fun `throws NotPerSeatPlanException when not per-seat`() {
            val nonPerSeatPlan =
                Plan(
                    id = 2L,
                    name = "Basic",
                    billingInterval = BillingInterval.MONTHLY,
                    basePrice = Money(BigDecimal("49.99"), Money.Currency.USD),
                    tier = PlanTier.PROFESSIONAL,
                    features = setOf("feature1"),
                    perSeatPricing = false,
                )
            val sub = sampleSubscription(plan = nonPerSeatPlan, seatCount = 5)
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            assertThrows(NotPerSeatPlanException::class.java) { service.updateSeatCount(1L, 6) }
        }

        // ST-E4: Same seat count
        @Test
        fun `throws SameSeatCountException when same count`() {
            val sub = sampleSubscription()
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            assertThrows(SameSeatCountException::class.java) { service.updateSeatCount(1L, 5) }
        }

        // ST-E5: Below minimum
        @Test
        fun `throws SeatCountOutOfRangeException when below minimum`() {
            val plan = perSeatPlan(minimumSeats = 2)
            val sub = sampleSubscription(plan = plan)
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            assertThrows(SeatCountOutOfRangeException::class.java) { service.updateSeatCount(1L, 1) }
        }

        // ST-E6: Above maximum
        @Test
        fun `throws SeatCountOutOfRangeException when above maximum`() {
            val plan = perSeatPlan(maximumSeats = 10)
            val sub = sampleSubscription(plan = plan)
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            assertThrows(SeatCountOutOfRangeException::class.java) { service.updateSeatCount(1L, 11) }
        }

        // ST-E9: Payment fails on increase
        @Test
        fun `throws PaymentFailedException on increase when gateway fails`() {
            val sub = sampleSubscription()
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { subscriptionAddOnRepository.findBySubscriptionIdAndStatus(1L, SubscriptionAddOnStatus.ACTIVE) } returns emptyList()
            every { paymentGateway.charge(any(), any(), any()) } returns PaymentResult(success = false, errorReason = "Declined")
            every { invoiceRepository.save(any()) } answers { firstArg() }

            assertThrows(PaymentFailedException::class.java) { service.updateSeatCount(1L, 6) }
        }
    }

    @Nested
    inner class SeatAddOnInteraction {
        // SA-1: Seat increase updates PER_SEAT add-on qty
        @Test
        fun `seat increase updates PER_SEAT add-on quantity`() {
            val sub = sampleSubscription()
            val sa = perSeatAddOn(sub)
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { subscriptionAddOnRepository.findBySubscriptionIdAndStatus(1L, SubscriptionAddOnStatus.ACTIVE) } returns listOf(sa)
            every { paymentGateway.charge(any(), any(), any()) } returns PaymentResult(success = true, transactionId = "tx-1")
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionRepository.save(any()) } answers { firstArg() }
            every { subscriptionAddOnRepository.save(any()) } answers { firstArg() }

            service.updateSeatCount(1L, 7)

            verify { subscriptionAddOnRepository.save(match { it.quantity == 7 }) }
        }

        // SA-2: Seat decrease updates PER_SEAT add-on qty
        @Test
        fun `seat decrease updates PER_SEAT add-on quantity`() {
            val sub = sampleSubscription()
            val sa = perSeatAddOn(sub)
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { subscriptionAddOnRepository.findBySubscriptionIdAndStatus(1L, SubscriptionAddOnStatus.ACTIVE) } returns listOf(sa)
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionRepository.save(any()) } answers { firstArg() }
            every { subscriptionAddOnRepository.save(any()) } answers { firstArg() }

            service.updateSeatCount(1L, 3)

            verify { subscriptionAddOnRepository.save(match { it.quantity == 3 }) }
        }

        // SA-3: Seat change does not affect FLAT add-on
        @Test
        fun `seat change does not affect FLAT add-on quantity`() {
            val sub = sampleSubscription()
            val flat = flatAddOn(sub)
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { subscriptionAddOnRepository.findBySubscriptionIdAndStatus(1L, SubscriptionAddOnStatus.ACTIVE) } returns listOf(flat)
            every { paymentGateway.charge(any(), any(), any()) } returns PaymentResult(success = true, transactionId = "tx-1")
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionRepository.save(any()) } answers { firstArg() }

            service.updateSeatCount(1L, 10)

            // FLAT add-on should NOT be saved (not PER_SEAT)
            verify(exactly = 0) { subscriptionAddOnRepository.save(any()) }
        }

        // SA-4: Seat increase with multiple PER_SEAT add-ons
        @Test
        fun `seat increase with multiple PER_SEAT add-ons updates both`() {
            val sub = sampleSubscription()
            val addOn2 =
                AddOn(
                    id = 21L,
                    name = "Analytics",
                    price = Money(BigDecimal("3.00"), Money.Currency.USD),
                    billingType = BillingType.PER_SEAT,
                    compatibleTiers = setOf(PlanTier.PROFESSIONAL),
                )
            val sa1 = perSeatAddOn(sub)
            val sa2 =
                SubscriptionAddOn(
                    id = 3L,
                    subscription = sub,
                    addOn = addOn2,
                    quantity = 5,
                    status = SubscriptionAddOnStatus.ACTIVE,
                    attachedAt = fixedInstant.minus(Duration.ofDays(10)),
                )

            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { subscriptionAddOnRepository.findBySubscriptionIdAndStatus(1L, SubscriptionAddOnStatus.ACTIVE) } returns listOf(sa1, sa2)
            every { paymentGateway.charge(any(), any(), any()) } returns PaymentResult(success = true, transactionId = "tx-1")
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionRepository.save(any()) } answers { firstArg() }
            every { subscriptionAddOnRepository.save(any()) } answers { firstArg() }

            service.updateSeatCount(1L, 8)

            verify(exactly = 2) { subscriptionAddOnRepository.save(match { it.quantity == 8 }) }
        }

        // SA-5: Seat increase proration includes add-on lines
        @Test
        fun `seat increase proration includes add-on charge lines`() {
            val sub = sampleSubscription()
            val sa = perSeatAddOn(sub)
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { subscriptionAddOnRepository.findBySubscriptionIdAndStatus(1L, SubscriptionAddOnStatus.ACTIVE) } returns listOf(sa)
            every { paymentGateway.charge(any(), any(), any()) } returns PaymentResult(success = true, transactionId = "tx-1")
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionRepository.save(any()) } answers { firstArg() }
            every { subscriptionAddOnRepository.save(any()) } answers { firstArg() }

            service.updateSeatCount(1L, 7)

            // Seat: 10.00 * 2 * 15/30 = 10.00
            // Add-on: 2.00 * 2 * 15/30 = 2.00
            // Total: 12.00
            verify {
                paymentGateway.charge(
                    match { it.amount == BigDecimal("12.00") },
                    any(),
                    any(),
                )
            }
        }

        // SA-6: Seat decrease credit includes add-on credit
        @Test
        fun `seat decrease credit includes add-on credit`() {
            val sub = sampleSubscription()
            val sa = perSeatAddOn(sub)
            every { subscriptionRepository.findByIdWithPlan(1L) } returns sub
            every { subscriptionAddOnRepository.findBySubscriptionIdAndStatus(1L, SubscriptionAddOnStatus.ACTIVE) } returns listOf(sa)
            every { invoiceRepository.save(any()) } answers { firstArg() }
            every { subscriptionRepository.save(any()) } answers { firstArg() }
            every { subscriptionAddOnRepository.save(any()) } answers { firstArg() }

            val result = service.updateSeatCount(1L, 3)

            // Seat credit: 10.00 * 2 * 15/30 = 10.00
            // Add-on credit: 2.00 * 2 * 15/30 = 2.00
            // Total: 12.00
            assertTrue(result.accountCreditBalance.amount.compareTo(BigDecimal("12.00")) == 0)
        }
    }
}
