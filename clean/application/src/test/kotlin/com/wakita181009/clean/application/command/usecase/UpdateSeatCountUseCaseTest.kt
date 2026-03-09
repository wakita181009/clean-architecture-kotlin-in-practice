package com.wakita181009.clean.application.command.usecase

import arrow.core.left
import arrow.core.right
import com.wakita181009.clean.application.command.dto.PaymentError
import com.wakita181009.clean.application.command.dto.PaymentResult
import com.wakita181009.clean.application.command.error.UpdateSeatCountError
import com.wakita181009.clean.application.command.port.AddOnQueryPort
import com.wakita181009.clean.application.command.port.PaymentGatewayPort
import com.wakita181009.clean.application.command.port.SubscriptionAddOnCommandQueryPort
import com.wakita181009.clean.application.command.port.SubscriptionCommandQueryPort
import com.wakita181009.clean.application.port.ClockPort
import com.wakita181009.clean.application.port.TransactionPort
import com.wakita181009.clean.domain.error.DomainError
import com.wakita181009.clean.domain.model.AddOn
import com.wakita181009.clean.domain.model.AddOnBillingType
import com.wakita181009.clean.domain.model.AddOnId
import com.wakita181009.clean.domain.model.BillingInterval
import com.wakita181009.clean.domain.model.Currency
import com.wakita181009.clean.domain.model.CustomerId
import com.wakita181009.clean.domain.model.Invoice
import com.wakita181009.clean.domain.model.InvoiceId
import com.wakita181009.clean.domain.model.Money
import com.wakita181009.clean.domain.model.PaymentMethod
import com.wakita181009.clean.domain.model.Plan
import com.wakita181009.clean.domain.model.PlanId
import com.wakita181009.clean.domain.model.PlanTier
import com.wakita181009.clean.domain.model.Subscription
import com.wakita181009.clean.domain.model.SubscriptionAddOn
import com.wakita181009.clean.domain.model.SubscriptionAddOnId
import com.wakita181009.clean.domain.model.SubscriptionAddOnStatus
import com.wakita181009.clean.domain.model.SubscriptionId
import com.wakita181009.clean.domain.model.SubscriptionStatus
import com.wakita181009.clean.domain.repository.InvoiceRepository
import com.wakita181009.clean.domain.repository.SubscriptionAddOnRepository
import com.wakita181009.clean.domain.repository.SubscriptionRepository
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.time.Instant

class UpdateSeatCountUseCaseTest : DescribeSpec({

    val subscriptionCommandQueryPort = mockk<SubscriptionCommandQueryPort>()
    val subscriptionAddOnCommandQueryPort = mockk<SubscriptionAddOnCommandQueryPort>()
    val addOnQueryPort = mockk<AddOnQueryPort>()
    val subscriptionAddOnRepository = mockk<SubscriptionAddOnRepository>()
    val subscriptionRepository = mockk<SubscriptionRepository>()
    val invoiceRepository = mockk<InvoiceRepository>()
    val paymentGatewayPort = mockk<PaymentGatewayPort>()
    val clockPort = mockk<ClockPort>()
    val transactionPort = mockk<TransactionPort>()

    val useCase = UpdateSeatCountUseCaseImpl(
        subscriptionCommandQueryPort = subscriptionCommandQueryPort,
        subscriptionAddOnCommandQueryPort = subscriptionAddOnCommandQueryPort,
        addOnQueryPort = addOnQueryPort,
        subscriptionAddOnRepository = subscriptionAddOnRepository,
        subscriptionRepository = subscriptionRepository,
        invoiceRepository = invoiceRepository,
        paymentGatewayPort = paymentGatewayPort,
        clockPort = clockPort,
        transactionPort = transactionPort,
    )

    val now = Instant.parse("2025-01-15T00:00:00Z")
    val periodStart = Instant.parse("2025-01-01T00:00:00Z")
    val periodEnd = Instant.parse("2025-01-31T00:00:00Z")
    val subId = SubscriptionId(1L).getOrNull()!!

    val perSeatPlan = Plan.of(
        id = PlanId(1L).getOrNull()!!, name = "Pro", billingInterval = BillingInterval.MONTHLY,
        basePrice = Money.of(BigDecimal("10.00"), Currency.USD).getOrNull()!!,
        usageLimit = null, features = setOf("api"), tier = PlanTier.PROFESSIONAL, active = true,
        perSeatPricing = true, minSeats = 1, maxSeats = 100,
    ).getOrNull()!!

    val nonPerSeatPlan = Plan.of(
        id = PlanId(2L).getOrNull()!!, name = "Basic", billingInterval = BillingInterval.MONTHLY,
        basePrice = Money.of(BigDecimal("10.00"), Currency.USD).getOrNull()!!,
        usageLimit = null, features = setOf("basic"), tier = PlanTier.STARTER, active = true,
    ).getOrNull()!!

    fun subscription(
        plan: Plan = perSeatPlan,
        seatCount: Int? = 5,
        status: SubscriptionStatus = SubscriptionStatus.Active,
    ) = Subscription(
        id = subId, customerId = CustomerId(1L).getOrNull()!!, plan = plan,
        status = status,
        currentPeriodStart = periodStart, currentPeriodEnd = periodEnd,
        trialStart = null, trialEnd = null, pausedAt = null, canceledAt = null,
        cancelAtPeriodEnd = false, gracePeriodEnd = null, pauseCountInPeriod = 0,
        paymentMethod = PaymentMethod.CREDIT_CARD, createdAt = periodStart, updatedAt = periodStart,
        seatCount = seatCount,
    )

    beforeTest {
        clearMocks(
            subscriptionCommandQueryPort, subscriptionAddOnCommandQueryPort, addOnQueryPort,
            subscriptionAddOnRepository, subscriptionRepository, invoiceRepository,
            paymentGatewayPort, clockPort, transactionPort,
        )
        every { clockPort.now() } returns now
        every { transactionPort.run(any<() -> Any>()) } answers { firstArg<() -> Any>()() }
    }

    describe("Seat increase (immediate charge)") {
        // ST-U1
        it("increases by 1 seat with prorated charge") {
            val sub = subscription()
            every { subscriptionCommandQueryPort.findById(subId) } returns sub.right()
            every { subscriptionAddOnCommandQueryPort.findActiveBySubscriptionId(subId) } returns emptyList<SubscriptionAddOn>().right()
            every { paymentGatewayPort.charge(any(), any(), any()) } returns PaymentResult("txn_1", now).right()
            every { invoiceRepository.save(any()) } answers { firstArg<Invoice>().copy(id = InvoiceId(1L).getOrNull()!!).right() }
            every { subscriptionRepository.save(any()) } answers { firstArg<Subscription>().right() }

            val result = useCase.execute(1L, 6).shouldBeRight()
            result.seatCount shouldBe 6
        }

        // ST-U2
        it("increases by 5 seats") {
            val sub = subscription()
            every { subscriptionCommandQueryPort.findById(subId) } returns sub.right()
            every { subscriptionAddOnCommandQueryPort.findActiveBySubscriptionId(subId) } returns emptyList<SubscriptionAddOn>().right()
            every { paymentGatewayPort.charge(any(), any(), any()) } returns PaymentResult("txn_1", now).right()
            every { invoiceRepository.save(any()) } answers { firstArg<Invoice>().copy(id = InvoiceId(1L).getOrNull()!!).right() }
            every { subscriptionRepository.save(any()) } answers { firstArg<Subscription>().right() }

            val result = useCase.execute(1L, 10).shouldBeRight()
            result.seatCount shouldBe 10
        }

        // ST-U4
        it("increases with PER_SEAT add-on proration") {
            val sub = subscription()
            val addOnId = AddOnId(2L).getOrNull()!!
            val subAddOn = SubscriptionAddOn(
                id = SubscriptionAddOnId(1L).getOrNull()!!, subscriptionId = subId,
                addOnId = addOnId, quantity = 5, status = SubscriptionAddOnStatus.Active,
                attachedAt = periodStart, detachedAt = null,
            )
            val perSeatAddOn = AddOn.of(
                id = addOnId, name = "Storage",
                price = Money.of(BigDecimal("2.00"), Currency.USD).getOrNull()!!,
                billingType = AddOnBillingType.PER_SEAT,
                compatibleTiers = setOf(PlanTier.PROFESSIONAL), active = true,
            ).getOrNull()!!

            every { subscriptionCommandQueryPort.findById(subId) } returns sub.right()
            every { subscriptionAddOnCommandQueryPort.findActiveBySubscriptionId(subId) } returns listOf(subAddOn).right()
            every { addOnQueryPort.findActiveById(addOnId) } returns (perSeatAddOn as AddOn?).right()
            every { paymentGatewayPort.charge(any(), any(), any()) } returns PaymentResult("txn_1", now).right()
            every { invoiceRepository.save(any()) } answers { firstArg<Invoice>().copy(id = InvoiceId(1L).getOrNull()!!).right() }
            every { subscriptionRepository.save(any()) } answers { firstArg<Subscription>().right() }
            every { subscriptionAddOnRepository.save(any()) } answers { firstArg<SubscriptionAddOn>().right() }

            val result = useCase.execute(1L, 7).shouldBeRight()
            result.seatCount shouldBe 7
        }
    }

    describe("Seat decrease (credit)") {
        // ST-D1
        it("decreases by 1 seat with credit to account") {
            val sub = subscription()
            every { subscriptionCommandQueryPort.findById(subId) } returns sub.right()
            every { subscriptionAddOnCommandQueryPort.findActiveBySubscriptionId(subId) } returns emptyList<SubscriptionAddOn>().right()
            every { invoiceRepository.save(any()) } answers { firstArg<Invoice>().copy(id = InvoiceId(1L).getOrNull()!!).right() }
            every { subscriptionRepository.save(any()) } answers { firstArg<Subscription>().right() }

            val result = useCase.execute(1L, 4).shouldBeRight()
            result.seatCount shouldBe 4
        }

        // ST-D2
        it("decreases to minimum") {
            val sub = subscription()
            every { subscriptionCommandQueryPort.findById(subId) } returns sub.right()
            every { subscriptionAddOnCommandQueryPort.findActiveBySubscriptionId(subId) } returns emptyList<SubscriptionAddOn>().right()
            every { invoiceRepository.save(any()) } answers { firstArg<Invoice>().copy(id = InvoiceId(1L).getOrNull()!!).right() }
            every { subscriptionRepository.save(any()) } answers { firstArg<Subscription>().right() }

            val result = useCase.execute(1L, 1).shouldBeRight()
            result.seatCount shouldBe 1
        }
    }

    describe("Validation errors") {
        // ST-E1
        it("returns error when subscription not found") {
            every { subscriptionCommandQueryPort.findById(subId) } returns (object : DomainError { override val message = "not found" }).left()
            useCase.execute(1L, 6).shouldBeLeft().shouldBeInstanceOf<UpdateSeatCountError.SubscriptionNotFound>()
        }

        // ST-E2
        it("returns error when not active") {
            val sub = subscription(status = SubscriptionStatus.Paused(now))
            every { subscriptionCommandQueryPort.findById(subId) } returns sub.right()
            useCase.execute(1L, 6).shouldBeLeft().shouldBeInstanceOf<UpdateSeatCountError.NotActive>()
        }

        // ST-E3
        it("returns error when not per-seat plan") {
            val sub = subscription(plan = nonPerSeatPlan, seatCount = null)
            every { subscriptionCommandQueryPort.findById(subId) } returns sub.right()
            useCase.execute(1L, 5).shouldBeLeft().shouldBeInstanceOf<UpdateSeatCountError.NotPerSeatPlan>()
        }

        // ST-E4
        it("returns error when same seat count") {
            val sub = subscription()
            every { subscriptionCommandQueryPort.findById(subId) } returns sub.right()
            useCase.execute(1L, 5).shouldBeLeft().shouldBeInstanceOf<UpdateSeatCountError.SameSeatCount>()
        }

        // ST-E5
        it("returns error when below minimum") {
            val planWithMinSeats = Plan.of(
                id = PlanId(1L).getOrNull()!!, name = "Pro", billingInterval = BillingInterval.MONTHLY,
                basePrice = Money.of(BigDecimal("10.00"), Currency.USD).getOrNull()!!,
                usageLimit = null, features = setOf("api"), tier = PlanTier.PROFESSIONAL, active = true,
                perSeatPricing = true, minSeats = 2, maxSeats = 100,
            ).getOrNull()!!
            val sub = subscription(plan = planWithMinSeats, seatCount = 5)
            every { subscriptionCommandQueryPort.findById(subId) } returns sub.right()
            useCase.execute(1L, 1).shouldBeLeft().shouldBeInstanceOf<UpdateSeatCountError.BelowMinimum>()
        }

        // ST-E6
        it("returns error when above maximum") {
            val planWithMaxSeats = Plan.of(
                id = PlanId(1L).getOrNull()!!, name = "Pro", billingInterval = BillingInterval.MONTHLY,
                basePrice = Money.of(BigDecimal("10.00"), Currency.USD).getOrNull()!!,
                usageLimit = null, features = setOf("api"), tier = PlanTier.PROFESSIONAL, active = true,
                perSeatPricing = true, minSeats = 1, maxSeats = 10,
            ).getOrNull()!!
            val sub = subscription(plan = planWithMaxSeats, seatCount = 5)
            every { subscriptionCommandQueryPort.findById(subId) } returns sub.right()
            useCase.execute(1L, 11).shouldBeLeft().shouldBeInstanceOf<UpdateSeatCountError.AboveMaximum>()
        }

        // ST-E7
        it("returns error for zero seats") {
            useCase.execute(1L, 0).shouldBeLeft().shouldBeInstanceOf<UpdateSeatCountError.InvalidInput>()
        }

        // ST-E8
        it("returns error for negative seats") {
            useCase.execute(1L, -1).shouldBeLeft().shouldBeInstanceOf<UpdateSeatCountError.InvalidInput>()
        }

        // ST-E9
        it("returns error when payment fails on increase") {
            val sub = subscription()
            every { subscriptionCommandQueryPort.findById(subId) } returns sub.right()
            every { subscriptionAddOnCommandQueryPort.findActiveBySubscriptionId(subId) } returns emptyList<SubscriptionAddOn>().right()
            every { paymentGatewayPort.charge(any(), any(), any()) } returns PaymentError.Declined().left()

            useCase.execute(1L, 6).shouldBeLeft().shouldBeInstanceOf<UpdateSeatCountError.PaymentFailed>()
        }

        // ST-E10
        it("returns error for invalid subscription ID") {
            useCase.execute(-1L, 6).shouldBeLeft().shouldBeInstanceOf<UpdateSeatCountError.InvalidInput>()
        }
    }
})
