package com.wakita181009.clean.application.command.usecase

import arrow.core.left
import arrow.core.right
import com.wakita181009.clean.application.command.dto.PaymentError
import com.wakita181009.clean.application.command.dto.PaymentResult
import com.wakita181009.clean.application.command.error.AttachAddOnError
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
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

class AttachAddOnUseCaseTest : DescribeSpec({

    val subscriptionCommandQueryPort = mockk<SubscriptionCommandQueryPort>()
    val addOnQueryPort = mockk<AddOnQueryPort>()
    val subscriptionAddOnCommandQueryPort = mockk<SubscriptionAddOnCommandQueryPort>()
    val subscriptionAddOnRepository = mockk<SubscriptionAddOnRepository>()
    val invoiceRepository = mockk<InvoiceRepository>()
    val paymentGatewayPort = mockk<PaymentGatewayPort>()
    val clockPort = mockk<ClockPort>()
    val transactionPort = mockk<TransactionPort>()

    val useCase = AttachAddOnUseCaseImpl(
        subscriptionCommandQueryPort = subscriptionCommandQueryPort,
        addOnQueryPort = addOnQueryPort,
        subscriptionAddOnCommandQueryPort = subscriptionAddOnCommandQueryPort,
        subscriptionAddOnRepository = subscriptionAddOnRepository,
        invoiceRepository = invoiceRepository,
        paymentGatewayPort = paymentGatewayPort,
        clockPort = clockPort,
        transactionPort = transactionPort,
    )

    val now = Instant.parse("2025-01-15T00:00:00Z")
    val periodStart = Instant.parse("2025-01-01T00:00:00Z")
    val periodEnd = Instant.parse("2025-01-31T00:00:00Z")
    val subId = SubscriptionId(1L).getOrNull()!!
    val customerId = CustomerId(1L).getOrNull()!!
    val addOnIdVal = AddOnId(1L).getOrNull()!!
    val planId = PlanId(1L).getOrNull()!!

    fun proPlan(perSeatPricing: Boolean = false) = Plan.of(
        id = planId, name = "Pro", billingInterval = BillingInterval.MONTHLY,
        basePrice = Money.of(BigDecimal("49.99"), Currency.USD).getOrNull()!!,
        usageLimit = null, features = setOf("api"), tier = PlanTier.PROFESSIONAL, active = true,
        perSeatPricing = perSeatPricing, minSeats = 1, maxSeats = 100,
    ).getOrNull()!!

    fun activeSubscription(
        plan: Plan = proPlan(),
        seatCount: Int? = null,
    ) = Subscription(
        id = subId, customerId = customerId, plan = plan,
        status = SubscriptionStatus.Active,
        currentPeriodStart = periodStart, currentPeriodEnd = periodEnd,
        trialStart = null, trialEnd = null, pausedAt = null, canceledAt = null,
        cancelAtPeriodEnd = false, gracePeriodEnd = null, pauseCountInPeriod = 0,
        paymentMethod = PaymentMethod.CREDIT_CARD, createdAt = periodStart, updatedAt = periodStart,
        seatCount = seatCount,
    )

    fun flatAddOn(tier: PlanTier = PlanTier.PROFESSIONAL, currency: Currency = Currency.USD) = AddOn.of(
        id = addOnIdVal, name = "Priority Support",
        price = Money.of(BigDecimal("9.99"), currency).getOrNull()!!,
        billingType = AddOnBillingType.FLAT, compatibleTiers = setOf(tier), active = true,
    ).getOrNull()!!

    fun perSeatAddOn() = AddOn.of(
        id = addOnIdVal, name = "Extra Storage",
        price = Money.of(BigDecimal("2.00"), Currency.USD).getOrNull()!!,
        billingType = AddOnBillingType.PER_SEAT,
        compatibleTiers = setOf(PlanTier.PROFESSIONAL), active = true,
    ).getOrNull()!!

    beforeTest {
        clearMocks(
            subscriptionCommandQueryPort, addOnQueryPort, subscriptionAddOnCommandQueryPort,
            subscriptionAddOnRepository, invoiceRepository, paymentGatewayPort, clockPort, transactionPort,
        )
        every { clockPort.now() } returns now
        every { transactionPort.run(any<() -> Any>()) } answers {
            firstArg<() -> Any>()()
        }
    }

    describe("Happy path") {
        // AO-H1
        it("attaches FLAT add-on with prorated charge") {
            val sub = activeSubscription()
            every { subscriptionCommandQueryPort.findById(subId) } returns sub.right()
            every { addOnQueryPort.findActiveById(addOnIdVal) } returns (flatAddOn() as AddOn?).right()
            every { subscriptionAddOnCommandQueryPort.findActiveBySubscriptionIdAndAddOnId(subId, addOnIdVal) } returns (null as SubscriptionAddOn?).right()
            every { subscriptionAddOnCommandQueryPort.findActiveBySubscriptionId(subId) } returns emptyList<SubscriptionAddOn>().right()
            every { paymentGatewayPort.charge(any(), any(), any()) } returns PaymentResult("txn_1", now).right()
            every { invoiceRepository.save(any()) } answers { firstArg<Invoice>().copy(id = com.wakita181009.clean.domain.model.InvoiceId(1L).getOrNull()!!).right() }
            every { subscriptionAddOnRepository.save(any()) } answers {
                firstArg<SubscriptionAddOn>().copy(id = SubscriptionAddOnId(1L).getOrNull()!!).right()
            }

            val result = useCase.execute(1L, 1L).shouldBeRight()
            result.quantity shouldBe 1
            result.status shouldBe SubscriptionAddOnStatus.Active
        }

        // AO-H2
        it("attaches PER_SEAT add-on with quantity = seat count") {
            val sub = activeSubscription(plan = proPlan(perSeatPricing = true), seatCount = 5)
            every { subscriptionCommandQueryPort.findById(subId) } returns sub.right()
            every { addOnQueryPort.findActiveById(addOnIdVal) } returns (perSeatAddOn() as AddOn?).right()
            every { subscriptionAddOnCommandQueryPort.findActiveBySubscriptionIdAndAddOnId(subId, addOnIdVal) } returns (null as SubscriptionAddOn?).right()
            every { subscriptionAddOnCommandQueryPort.findActiveBySubscriptionId(subId) } returns emptyList<SubscriptionAddOn>().right()
            every { paymentGatewayPort.charge(any(), any(), any()) } returns PaymentResult("txn_1", now).right()
            every { invoiceRepository.save(any()) } answers { firstArg<Invoice>().copy(id = com.wakita181009.clean.domain.model.InvoiceId(1L).getOrNull()!!).right() }
            every { subscriptionAddOnRepository.save(any()) } answers {
                firstArg<SubscriptionAddOn>().copy(id = SubscriptionAddOnId(1L).getOrNull()!!).right()
            }

            val result = useCase.execute(1L, 1L).shouldBeRight()
            result.quantity shouldBe 5
        }

        // AO-H5
        it("attaches 5th add-on (at limit)") {
            val sub = activeSubscription()
            val existingAddOns = (1..4).map {
                SubscriptionAddOn(
                    id = SubscriptionAddOnId(it.toLong()).getOrNull()!!, subscriptionId = subId,
                    addOnId = AddOnId(it.toLong()).getOrNull()!!, quantity = 1,
                    status = SubscriptionAddOnStatus.Active, attachedAt = now, detachedAt = null,
                )
            }
            every { subscriptionCommandQueryPort.findById(subId) } returns sub.right()
            every { addOnQueryPort.findActiveById(addOnIdVal) } returns (flatAddOn() as AddOn?).right()
            every { subscriptionAddOnCommandQueryPort.findActiveBySubscriptionIdAndAddOnId(subId, addOnIdVal) } returns (null as SubscriptionAddOn?).right()
            every { subscriptionAddOnCommandQueryPort.findActiveBySubscriptionId(subId) } returns existingAddOns.right()
            every { paymentGatewayPort.charge(any(), any(), any()) } returns PaymentResult("txn_1", now).right()
            every { invoiceRepository.save(any()) } answers { firstArg<Invoice>().copy(id = com.wakita181009.clean.domain.model.InvoiceId(1L).getOrNull()!!).right() }
            every { subscriptionAddOnRepository.save(any()) } answers {
                firstArg<SubscriptionAddOn>().copy(id = SubscriptionAddOnId(5L).getOrNull()!!).right()
            }

            useCase.execute(1L, 1L).shouldBeRight()
        }
    }

    describe("Validation and business rule errors") {
        // AO-E1
        it("returns error when subscription not found") {
            every { subscriptionCommandQueryPort.findById(subId) } returns (object : DomainError { override val message = "not found" }).left()

            useCase.execute(1L, 1L).shouldBeLeft().shouldBeInstanceOf<AttachAddOnError.SubscriptionNotFound>()
        }

        // AO-E2
        it("returns error when subscription is Paused") {
            val sub = activeSubscription().copy(status = SubscriptionStatus.Paused(now))
            every { subscriptionCommandQueryPort.findById(subId) } returns sub.right()

            useCase.execute(1L, 1L).shouldBeLeft().shouldBeInstanceOf<AttachAddOnError.NotActive>()
        }

        // AO-E3
        it("returns error when subscription is Trial") {
            val sub = activeSubscription().copy(status = SubscriptionStatus.Trial)
            every { subscriptionCommandQueryPort.findById(subId) } returns sub.right()

            useCase.execute(1L, 1L).shouldBeLeft().shouldBeInstanceOf<AttachAddOnError.NotActive>()
        }

        // AO-E4
        it("returns error when add-on not found") {
            val sub = activeSubscription()
            every { subscriptionCommandQueryPort.findById(subId) } returns sub.right()
            every { addOnQueryPort.findActiveById(addOnIdVal) } returns (null as AddOn?).right()

            useCase.execute(1L, 1L).shouldBeLeft().shouldBeInstanceOf<AttachAddOnError.AddOnNotFound>()
        }

        // AO-E5
        it("returns error when add-on inactive") {
            val sub = activeSubscription()
            val inactiveAddOn = flatAddOn().copy(active = false)
            every { subscriptionCommandQueryPort.findById(subId) } returns sub.right()
            every { addOnQueryPort.findActiveById(addOnIdVal) } returns (inactiveAddOn as AddOn?).right()

            useCase.execute(1L, 1L).shouldBeLeft().shouldBeInstanceOf<AttachAddOnError.AddOnNotFound>()
        }

        // AO-E6
        it("returns error on currency mismatch") {
            val sub = activeSubscription()
            val eurAddOn = flatAddOn(currency = Currency.EUR)
            every { subscriptionCommandQueryPort.findById(subId) } returns sub.right()
            every { addOnQueryPort.findActiveById(addOnIdVal) } returns (eurAddOn as AddOn?).right()

            useCase.execute(1L, 1L).shouldBeLeft().shouldBeInstanceOf<AttachAddOnError.CurrencyMismatch>()
        }

        // AO-E7
        it("returns error on tier incompatibility") {
            val sub = activeSubscription()
            val proOnlyAddOn = AddOn.of(
                id = addOnIdVal, name = "Pro Only",
                price = Money.of(BigDecimal("9.99"), Currency.USD).getOrNull()!!,
                billingType = AddOnBillingType.FLAT,
                compatibleTiers = setOf(PlanTier.ENTERPRISE), active = true,
            ).getOrNull()!!
            every { subscriptionCommandQueryPort.findById(subId) } returns sub.right()
            every { addOnQueryPort.findActiveById(addOnIdVal) } returns (proOnlyAddOn as AddOn?).right()

            useCase.execute(1L, 1L).shouldBeLeft().shouldBeInstanceOf<AttachAddOnError.TierIncompatible>()
        }

        // AO-E8
        it("returns error for PER_SEAT on non-per-seat plan") {
            val sub = activeSubscription()
            every { subscriptionCommandQueryPort.findById(subId) } returns sub.right()
            every { addOnQueryPort.findActiveById(addOnIdVal) } returns (perSeatAddOn() as AddOn?).right()

            useCase.execute(1L, 1L).shouldBeLeft().shouldBeInstanceOf<AttachAddOnError.PerSeatOnNonPerSeatPlan>()
        }

        // AO-E9
        it("returns error for duplicate add-on") {
            val sub = activeSubscription()
            val existing = SubscriptionAddOn(
                id = SubscriptionAddOnId(1L).getOrNull()!!, subscriptionId = subId,
                addOnId = addOnIdVal, quantity = 1, status = SubscriptionAddOnStatus.Active,
                attachedAt = now, detachedAt = null,
            )
            every { subscriptionCommandQueryPort.findById(subId) } returns sub.right()
            every { addOnQueryPort.findActiveById(addOnIdVal) } returns (flatAddOn() as AddOn?).right()
            every { subscriptionAddOnCommandQueryPort.findActiveBySubscriptionIdAndAddOnId(subId, addOnIdVal) } returns (existing as SubscriptionAddOn?).right()

            useCase.execute(1L, 1L).shouldBeLeft().shouldBeInstanceOf<AttachAddOnError.DuplicateAddOn>()
        }

        // AO-E10
        it("returns error when limit reached (5 active add-ons)") {
            val sub = activeSubscription()
            val existingAddOns = (1..5).map {
                SubscriptionAddOn(
                    id = SubscriptionAddOnId(it.toLong()).getOrNull()!!, subscriptionId = subId,
                    addOnId = AddOnId(it.toLong()).getOrNull()!!, quantity = 1,
                    status = SubscriptionAddOnStatus.Active, attachedAt = now, detachedAt = null,
                )
            }
            every { subscriptionCommandQueryPort.findById(subId) } returns sub.right()
            every { addOnQueryPort.findActiveById(addOnIdVal) } returns (flatAddOn() as AddOn?).right()
            every { subscriptionAddOnCommandQueryPort.findActiveBySubscriptionIdAndAddOnId(subId, addOnIdVal) } returns (null as SubscriptionAddOn?).right()
            every { subscriptionAddOnCommandQueryPort.findActiveBySubscriptionId(subId) } returns existingAddOns.right()

            useCase.execute(1L, 1L).shouldBeLeft().shouldBeInstanceOf<AttachAddOnError.AddOnLimitReached>()
        }

        // AO-E11
        it("returns error when payment fails") {
            val sub = activeSubscription()
            every { subscriptionCommandQueryPort.findById(subId) } returns sub.right()
            every { addOnQueryPort.findActiveById(addOnIdVal) } returns (flatAddOn() as AddOn?).right()
            every { subscriptionAddOnCommandQueryPort.findActiveBySubscriptionIdAndAddOnId(subId, addOnIdVal) } returns (null as SubscriptionAddOn?).right()
            every { subscriptionAddOnCommandQueryPort.findActiveBySubscriptionId(subId) } returns emptyList<SubscriptionAddOn>().right()
            every { paymentGatewayPort.charge(any(), any(), any()) } returns PaymentError.Declined().left()

            useCase.execute(1L, 1L).shouldBeLeft().shouldBeInstanceOf<AttachAddOnError.PaymentFailed>()
        }

        // AO-E12
        it("returns error for invalid subscription ID") {
            useCase.execute(-1L, 1L).shouldBeLeft().shouldBeInstanceOf<AttachAddOnError.InvalidInput>()
        }
    }
})
