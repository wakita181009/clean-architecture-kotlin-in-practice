package com.wakita181009.clean.application.command.usecase

import arrow.core.right
import com.wakita181009.clean.application.command.dto.PaymentResult
import com.wakita181009.clean.application.command.port.AddOnQueryPort
import com.wakita181009.clean.application.command.port.PaymentGatewayPort
import com.wakita181009.clean.application.command.port.SubscriptionAddOnCommandQueryPort
import com.wakita181009.clean.application.command.port.SubscriptionCommandQueryPort
import com.wakita181009.clean.application.command.port.UsageQueryPort
import com.wakita181009.clean.application.port.ClockPort
import com.wakita181009.clean.application.port.TransactionPort
import com.wakita181009.clean.domain.model.AddOn
import com.wakita181009.clean.domain.model.AddOnBillingType
import com.wakita181009.clean.domain.model.AddOnId
import com.wakita181009.clean.domain.model.BillingInterval
import com.wakita181009.clean.domain.model.Currency
import com.wakita181009.clean.domain.model.CustomerId
import com.wakita181009.clean.domain.model.Discount
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
import com.wakita181009.clean.domain.model.UsageRecord
import com.wakita181009.clean.domain.repository.DiscountRepository
import com.wakita181009.clean.domain.repository.InvoiceRepository
import com.wakita181009.clean.domain.repository.SubscriptionRepository
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.math.BigDecimal
import java.time.Instant

class ProcessRenewalPhase1Test : DescribeSpec({

    val subscriptionCommandQueryPort = mockk<SubscriptionCommandQueryPort>()
    val usageQueryPort = mockk<UsageQueryPort>()
    val paymentGatewayPort = mockk<PaymentGatewayPort>()
    val subscriptionRepository = mockk<SubscriptionRepository>()
    val invoiceRepository = mockk<InvoiceRepository>()
    val discountRepository = mockk<DiscountRepository>()
    val clockPort = mockk<ClockPort>()
    val transactionPort = mockk<TransactionPort>()
    val subscriptionAddOnCommandQueryPort = mockk<SubscriptionAddOnCommandQueryPort>()
    val addOnQueryPort = mockk<AddOnQueryPort>()

    val useCase = ProcessRenewalUseCaseImpl(
        subscriptionCommandQueryPort = subscriptionCommandQueryPort,
        usageQueryPort = usageQueryPort,
        paymentGatewayPort = paymentGatewayPort,
        subscriptionRepository = subscriptionRepository,
        invoiceRepository = invoiceRepository,
        discountRepository = discountRepository,
        clockPort = clockPort,
        transactionPort = transactionPort,
        subscriptionAddOnCommandQueryPort = subscriptionAddOnCommandQueryPort,
        addOnQueryPort = addOnQueryPort,
    )

    val now = Instant.parse("2025-02-01T00:00:00Z")
    val periodStart = Instant.parse("2025-01-01T00:00:00Z")
    val periodEnd = Instant.parse("2025-01-31T00:00:00Z")
    val subId = SubscriptionId(1L).getOrNull()!!
    val planId = PlanId(1L).getOrNull()!!

    fun plan(perSeat: Boolean = false, price: BigDecimal = BigDecimal("10.00")) = Plan.of(
        id = planId, name = "Pro", billingInterval = BillingInterval.MONTHLY,
        basePrice = Money.of(price, Currency.USD).getOrNull()!!,
        usageLimit = null, features = setOf("api"), tier = PlanTier.PROFESSIONAL, active = true,
        perSeatPricing = perSeat, minSeats = 1, maxSeats = 100,
    ).getOrNull()!!

    fun sub(
        plan: Plan = plan(),
        seatCount: Int? = null,
        accountCreditBalance: Money = Money.zero(Currency.USD),
    ) = Subscription(
        id = subId, customerId = CustomerId(1L).getOrNull()!!, plan = plan,
        status = SubscriptionStatus.Active,
        currentPeriodStart = periodStart, currentPeriodEnd = periodEnd,
        trialStart = null, trialEnd = null, pausedAt = null, canceledAt = null,
        cancelAtPeriodEnd = false, gracePeriodEnd = null, pauseCountInPeriod = 0,
        paymentMethod = PaymentMethod.CREDIT_CARD, createdAt = periodStart, updatedAt = periodStart,
        seatCount = seatCount, accountCreditBalance = accountCreditBalance,
    )

    val invoiceSlot = slot<Invoice>()

    beforeTest {
        clearMocks(
            subscriptionCommandQueryPort, usageQueryPort, paymentGatewayPort, subscriptionRepository,
            invoiceRepository, discountRepository, clockPort, transactionPort,
            subscriptionAddOnCommandQueryPort, addOnQueryPort,
        )
        every { clockPort.now() } returns now
        every { transactionPort.run(any<() -> Any>()) } answers { firstArg<() -> Any>()() }
        every { usageQueryPort.findForPeriod(any(), any(), any()) } returns emptyList<UsageRecord>().right()
    }

    describe("Add-on + Renewal Interaction") {
        // AR-1
        it("renewal includes FLAT add-on charge") {
            val s = sub()
            val flatAddOn = AddOn.of(
                id = AddOnId(1L).getOrNull()!!, name = "Support",
                price = Money.of(BigDecimal("9.99"), Currency.USD).getOrNull()!!,
                billingType = AddOnBillingType.FLAT,
                compatibleTiers = setOf(PlanTier.PROFESSIONAL), active = true,
            ).getOrNull()!!
            val subAddOn = SubscriptionAddOn(
                id = SubscriptionAddOnId(1L).getOrNull()!!, subscriptionId = subId,
                addOnId = AddOnId(1L).getOrNull()!!, quantity = 1,
                status = SubscriptionAddOnStatus.Active, attachedAt = periodStart, detachedAt = null,
            )

            every { subscriptionCommandQueryPort.findByIdWithDiscount(subId) } returns Pair(s, null as Discount?).right()
            every { subscriptionAddOnCommandQueryPort.findActiveBySubscriptionId(subId) } returns listOf(subAddOn).right()
            every { addOnQueryPort.findActiveById(AddOnId(1L).getOrNull()!!) } returns (flatAddOn as AddOn?).right()
            every { paymentGatewayPort.charge(any(), any(), any()) } returns PaymentResult("txn", now).right()
            every { invoiceRepository.save(capture(invoiceSlot)) } answers { firstArg<Invoice>().copy(id = InvoiceId(1L).getOrNull()!!).right() }
            every { subscriptionRepository.save(any()) } answers { firstArg<Subscription>().right() }

            useCase.execute(1L).shouldBeRight()

            val lineTypes = invoiceSlot.captured.lineItems.map { it.type.name }
            lineTypes.contains("ADDON_CHARGE") shouldBe true
        }

        // AR-6
        it("per-seat plan renewal charges per seat") {
            val perSeatPlan = plan(perSeat = true)
            val s = sub(plan = perSeatPlan, seatCount = 5)

            every { subscriptionCommandQueryPort.findByIdWithDiscount(subId) } returns Pair(s, null as Discount?).right()
            every { subscriptionAddOnCommandQueryPort.findActiveBySubscriptionId(subId) } returns emptyList<SubscriptionAddOn>().right()
            every { paymentGatewayPort.charge(any(), any(), any()) } returns PaymentResult("txn", now).right()
            every { invoiceRepository.save(capture(invoiceSlot)) } answers { firstArg<Invoice>().copy(id = InvoiceId(1L).getOrNull()!!).right() }
            every { subscriptionRepository.save(any()) } answers { firstArg<Subscription>().right() }

            useCase.execute(1L).shouldBeRight()

            // Plan charge should be 10.00 * 5 = 50.00
            val planCharge = invoiceSlot.captured.lineItems.first { it.type.name == "PLAN_CHARGE" }
            planCharge.amount.amount shouldBe BigDecimal("50.00")
        }
    }

    describe("Credit + Renewal Interaction") {
        // CR-1
        it("account credit applied to renewal") {
            val s = sub(accountCreditBalance = Money.of(BigDecimal("15.00"), Currency.USD).getOrNull()!!)

            every { subscriptionCommandQueryPort.findByIdWithDiscount(subId) } returns Pair(s, null as Discount?).right()
            every { subscriptionAddOnCommandQueryPort.findActiveBySubscriptionId(subId) } returns emptyList<SubscriptionAddOn>().right()
            every { paymentGatewayPort.charge(any(), any(), any()) } returns PaymentResult("txn", now).right()
            every { invoiceRepository.save(capture(invoiceSlot)) } answers { firstArg<Invoice>().copy(id = InvoiceId(1L).getOrNull()!!).right() }
            every { subscriptionRepository.save(any()) } answers { firstArg<Subscription>().right() }

            useCase.execute(1L).shouldBeRight()

            val creditLines = invoiceSlot.captured.lineItems.filter { it.type.name == "ACCOUNT_CREDIT" }
            creditLines.size shouldBe 1
        }

        // CR-5
        it("no account credit line when balance is zero") {
            val s = sub(accountCreditBalance = Money.zero(Currency.USD))

            every { subscriptionCommandQueryPort.findByIdWithDiscount(subId) } returns Pair(s, null as Discount?).right()
            every { subscriptionAddOnCommandQueryPort.findActiveBySubscriptionId(subId) } returns emptyList<SubscriptionAddOn>().right()
            every { paymentGatewayPort.charge(any(), any(), any()) } returns PaymentResult("txn", now).right()
            every { invoiceRepository.save(capture(invoiceSlot)) } answers { firstArg<Invoice>().copy(id = InvoiceId(1L).getOrNull()!!).right() }
            every { subscriptionRepository.save(any()) } answers { firstArg<Subscription>().right() }

            useCase.execute(1L).shouldBeRight()

            val creditLines = invoiceSlot.captured.lineItems.filter { it.type.name == "ACCOUNT_CREDIT" }
            creditLines.size shouldBe 0
        }
    }
})
