package com.wakita181009.clean.application.command.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.wakita181009.clean.application.command.error.AttachAddOnError
import com.wakita181009.clean.application.command.port.AddOnQueryPort
import com.wakita181009.clean.application.command.port.PaymentGatewayPort
import com.wakita181009.clean.application.command.port.SubscriptionAddOnCommandQueryPort
import com.wakita181009.clean.application.command.port.SubscriptionCommandQueryPort
import com.wakita181009.clean.application.port.ClockPort
import com.wakita181009.clean.application.port.TransactionPort
import com.wakita181009.clean.domain.model.AddOnBillingType
import com.wakita181009.clean.domain.model.AddOnId
import com.wakita181009.clean.domain.model.Invoice
import com.wakita181009.clean.domain.model.InvoiceLineItem
import com.wakita181009.clean.domain.model.InvoiceLineItemType
import com.wakita181009.clean.domain.model.InvoiceStatus
import com.wakita181009.clean.domain.model.Money
import com.wakita181009.clean.domain.model.SubscriptionAddOn
import com.wakita181009.clean.domain.model.SubscriptionAddOnStatus
import com.wakita181009.clean.domain.model.SubscriptionId
import com.wakita181009.clean.domain.model.SubscriptionStatus
import com.wakita181009.clean.domain.repository.InvoiceRepository
import com.wakita181009.clean.domain.repository.SubscriptionAddOnRepository
import java.time.Duration
import java.time.ZoneOffset

interface AttachAddOnUseCase {
    fun execute(subscriptionId: Long, addOnId: Long): Either<AttachAddOnError, SubscriptionAddOn>
}

class AttachAddOnUseCaseImpl(
    private val subscriptionCommandQueryPort: SubscriptionCommandQueryPort,
    private val addOnQueryPort: AddOnQueryPort,
    private val subscriptionAddOnCommandQueryPort: SubscriptionAddOnCommandQueryPort,
    private val subscriptionAddOnRepository: SubscriptionAddOnRepository,
    private val invoiceRepository: InvoiceRepository,
    private val paymentGatewayPort: PaymentGatewayPort,
    private val clockPort: ClockPort,
    private val transactionPort: TransactionPort,
) : AttachAddOnUseCase {

    override fun execute(subscriptionId: Long, addOnId: Long): Either<AttachAddOnError, SubscriptionAddOn> = either {
        val subId = SubscriptionId(subscriptionId)
            .mapLeft { AttachAddOnError.InvalidInput("subscriptionId", it.message) }
            .bind()

        val aoId = AddOnId(addOnId)
            .mapLeft { AttachAddOnError.InvalidInput("addOnId", it.message) }
            .bind()

        val subscription = subscriptionCommandQueryPort.findById(subId)
            .mapLeft { AttachAddOnError.SubscriptionNotFound }
            .bind()

        ensure(subscription.status is SubscriptionStatus.Active) { AttachAddOnError.NotActive }

        val addOn = addOnQueryPort.findActiveById(aoId)
            .mapLeft { AttachAddOnError.Domain(it) }
            .bind()
            .let { ensureNotNull(it) { AttachAddOnError.AddOnNotFound } }

        ensure(addOn.active) { AttachAddOnError.AddOnNotFound }
        ensure(addOn.price.currency == subscription.plan.basePrice.currency) { AttachAddOnError.CurrencyMismatch }
        ensure(subscription.plan.tier in addOn.compatibleTiers) { AttachAddOnError.TierIncompatible }

        if (addOn.billingType == AddOnBillingType.PER_SEAT) {
            ensure(subscription.plan.perSeatPricing) { AttachAddOnError.PerSeatOnNonPerSeatPlan }
        }

        val existingAddOn = subscriptionAddOnCommandQueryPort.findActiveBySubscriptionIdAndAddOnId(subId, aoId)
            .mapLeft { AttachAddOnError.Domain(it) }
            .bind()
        ensure(existingAddOn == null) { AttachAddOnError.DuplicateAddOn }

        val activeAddOns = subscriptionAddOnCommandQueryPort.findActiveBySubscriptionId(subId)
            .mapLeft { AttachAddOnError.Domain(it) }
            .bind()
        ensure(activeAddOns.size < 5) { AttachAddOnError.AddOnLimitReached }

        val now = clockPort.now()
        val totalDays = Duration.between(subscription.currentPeriodStart, subscription.currentPeriodEnd).toDays()
        val daysRemaining = Duration.between(now, subscription.currentPeriodEnd).toDays().coerceAtLeast(0)

        val quantity = if (addOn.billingType == AddOnBillingType.PER_SEAT) {
            subscription.seatCount ?: 1
        } else {
            1
        }

        val proratedCharge = if (addOn.billingType == AddOnBillingType.PER_SEAT) {
            addOn.price.multiply(quantity.toLong() * daysRemaining, totalDays)
        } else {
            addOn.price.multiply(daysRemaining, totalDays)
        }

        val currency = subscription.plan.basePrice.currency
        val lineItem = InvoiceLineItem(
            description = "Proration charge for add-on: ${addOn.name}",
            amount = proratedCharge,
            type = InvoiceLineItemType.ADDON_PRORATION_CHARGE,
        )

        val invoice = Invoice(
            id = null,
            subscriptionId = subscription.id!!,
            lineItems = listOf(lineItem),
            subtotal = proratedCharge,
            discountAmount = Money.zero(currency),
            total = proratedCharge,
            currency = currency,
            status = InvoiceStatus.Draft,
            dueDate = now.atOffset(ZoneOffset.UTC).toLocalDate(),
            paidAt = null,
            paymentAttemptCount = 0,
            createdAt = now,
            updatedAt = now,
        )

        // Charge immediately
        val paymentResult = paymentGatewayPort.charge(
            proratedCharge,
            subscription.paymentMethod!!,
            subscription.customerId.value.toString(),
        ).mapLeft { AttachAddOnError.PaymentFailed(it.reason) }
            .bind()

        // Payment succeeded - save invoice and attach add-on
        transactionPort.run {
            invoiceRepository.save(
                invoice.copy(
                    status = InvoiceStatus.Open.pay(now).getOrNull()!!,
                    paidAt = now,
                    paymentAttemptCount = 1,
                ),
            ).mapLeft { AttachAddOnError.Domain(it) }
        }.bind()

        val subscriptionAddOn = SubscriptionAddOn(
            id = null,
            subscriptionId = subscription.id!!,
            addOnId = aoId,
            quantity = quantity,
            status = SubscriptionAddOnStatus.Active,
            attachedAt = now,
            detachedAt = null,
        )

        transactionPort.run {
            subscriptionAddOnRepository.save(subscriptionAddOn)
                .mapLeft { AttachAddOnError.Domain(it) }
        }.bind()
    }
}
