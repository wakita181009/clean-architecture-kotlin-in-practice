package com.wakita181009.clean.application.command.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.wakita181009.clean.application.command.dto.ChangePlanCommand
import com.wakita181009.clean.application.command.error.PlanChangeError
import com.wakita181009.clean.application.command.port.PaymentGatewayPort
import com.wakita181009.clean.application.command.port.PlanQueryPort
import com.wakita181009.clean.application.command.port.SubscriptionCommandQueryPort
import com.wakita181009.clean.application.port.ClockPort
import com.wakita181009.clean.application.port.TransactionPort
import com.wakita181009.clean.domain.model.Currency
import com.wakita181009.clean.domain.model.InvoiceLineItem
import com.wakita181009.clean.domain.model.InvoiceStatus
import com.wakita181009.clean.domain.model.Invoice
import com.wakita181009.clean.domain.model.Money
import com.wakita181009.clean.domain.model.PlanId
import com.wakita181009.clean.domain.model.Subscription
import com.wakita181009.clean.domain.model.SubscriptionId
import com.wakita181009.clean.domain.model.SubscriptionStatus
import com.wakita181009.clean.domain.repository.InvoiceRepository
import com.wakita181009.clean.domain.repository.SubscriptionRepository
import com.wakita181009.clean.domain.service.ProrationDomainService
import java.time.Duration
import java.time.ZoneOffset

interface PlanChangeUseCase {
    fun execute(command: ChangePlanCommand): Either<PlanChangeError, Subscription>
}

class PlanChangeUseCaseImpl(
    private val subscriptionCommandQueryPort: SubscriptionCommandQueryPort,
    private val planQueryPort: PlanQueryPort,
    private val paymentGatewayPort: PaymentGatewayPort,
    private val prorationDomainService: ProrationDomainService,
    private val subscriptionRepository: SubscriptionRepository,
    private val invoiceRepository: InvoiceRepository,
    private val clockPort: ClockPort,
    private val transactionPort: TransactionPort,
) : PlanChangeUseCase {

    override fun execute(command: ChangePlanCommand): Either<PlanChangeError, Subscription> = either {
        val subscriptionId = SubscriptionId(command.subscriptionId)
            .mapLeft { PlanChangeError.InvalidInput("subscriptionId", it.message) }
            .bind()

        val newPlanId = PlanId(command.newPlanId)
            .mapLeft { PlanChangeError.InvalidInput("newPlanId", it.message) }
            .bind()

        val subscription = subscriptionCommandQueryPort.findById(subscriptionId)
            .mapLeft { PlanChangeError.SubscriptionNotFound }
            .bind()

        ensure(subscription.status is SubscriptionStatus.Active) { PlanChangeError.NotActive }
        ensure(subscription.plan.id != newPlanId) { PlanChangeError.SamePlan }

        val newPlan = planQueryPort.findActiveById(newPlanId)
            .mapLeft { PlanChangeError.Domain(it) }
            .bind()
            .let { ensureNotNull(it) { PlanChangeError.PlanNotFound } }

        ensure(subscription.plan.basePrice.currency == newPlan.basePrice.currency) { PlanChangeError.CurrencyMismatch }

        val now = clockPort.now()
        val totalDays = Duration.between(subscription.currentPeriodStart, subscription.currentPeriodEnd).toDays()
        val daysRemaining = Duration.between(now, subscription.currentPeriodEnd).toDays().coerceAtLeast(0)

        val prorationResult = prorationDomainService.calculateProration(
            subscription.plan, newPlan, daysRemaining, totalDays,
        ).mapLeft { PlanChangeError.Domain(it) }.bind()

        val currency = subscription.plan.basePrice.currency
        val lineItems = listOf(prorationResult.credit, prorationResult.charge)
        val subtotal = prorationResult.netAmount
        val discountAmount = Money.zero(currency)
        val total = subtotal

        val invoice = Invoice(
            id = null,
            subscriptionId = subscription.id!!,
            lineItems = lineItems,
            subtotal = subtotal,
            discountAmount = discountAmount,
            total = total,
            currency = currency,
            status = InvoiceStatus.Draft,
            dueDate = now.atOffset(ZoneOffset.UTC).toLocalDate(),
            paidAt = null,
            paymentAttemptCount = 0,
            createdAt = now,
            updatedAt = now,
        )

        // If upgrade (positive net), charge immediately
        if (prorationResult.netAmount.isPositive()) {
            val paymentResult = paymentGatewayPort.charge(
                prorationResult.netAmount,
                subscription.paymentMethod!!,
                subscription.customerId.value.toString(),
            ).mapLeft { PlanChangeError.PaymentFailed(it.reason) }

            if (paymentResult.isLeft()) {
                // Payment failed: do NOT change plan
                paymentResult.bind()
            }
        }

        transactionPort.run {
            val finalizedInvoice = if (prorationResult.netAmount.isPositive()) {
                invoice.copy(
                    status = InvoiceStatus.Open.pay(now).getOrNull()!!,
                    paidAt = now,
                    paymentAttemptCount = 1,
                )
            } else {
                invoice.copy(status = InvoiceStatus.Open)
            }

            invoiceRepository.save(finalizedInvoice)
                .mapLeft { PlanChangeError.Domain(it) }
        }.bind()

        val updatedSubscription = subscription.copy(
            plan = newPlan,
            updatedAt = now,
        )

        transactionPort.run {
            subscriptionRepository.save(updatedSubscription)
                .mapLeft { PlanChangeError.Domain(it) }
        }.bind()
    }
}
