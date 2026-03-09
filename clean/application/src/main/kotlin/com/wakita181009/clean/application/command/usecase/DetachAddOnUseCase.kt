package com.wakita181009.clean.application.command.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.wakita181009.clean.application.command.error.DetachAddOnError
import com.wakita181009.clean.application.command.port.AddOnQueryPort
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
import com.wakita181009.clean.domain.model.Subscription
import com.wakita181009.clean.domain.model.SubscriptionAddOn
import com.wakita181009.clean.domain.model.SubscriptionAddOnStatus
import com.wakita181009.clean.domain.model.SubscriptionId
import com.wakita181009.clean.domain.model.SubscriptionStatus
import com.wakita181009.clean.domain.repository.InvoiceRepository
import com.wakita181009.clean.domain.repository.SubscriptionAddOnRepository
import com.wakita181009.clean.domain.repository.SubscriptionRepository
import java.time.Duration
import java.time.ZoneOffset

interface DetachAddOnUseCase {
    fun execute(subscriptionId: Long, addOnId: Long): Either<DetachAddOnError, SubscriptionAddOn>
}

class DetachAddOnUseCaseImpl(
    private val subscriptionCommandQueryPort: SubscriptionCommandQueryPort,
    private val addOnQueryPort: AddOnQueryPort,
    private val subscriptionAddOnCommandQueryPort: SubscriptionAddOnCommandQueryPort,
    private val subscriptionAddOnRepository: SubscriptionAddOnRepository,
    private val invoiceRepository: InvoiceRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val clockPort: ClockPort,
    private val transactionPort: TransactionPort,
) : DetachAddOnUseCase {

    override fun execute(subscriptionId: Long, addOnId: Long): Either<DetachAddOnError, SubscriptionAddOn> = either {
        val subId = SubscriptionId(subscriptionId)
            .mapLeft { DetachAddOnError.InvalidInput("subscriptionId", it.message) }
            .bind()

        val aoId = AddOnId(addOnId)
            .mapLeft { DetachAddOnError.InvalidInput("addOnId", it.message) }
            .bind()

        val subscription = subscriptionCommandQueryPort.findById(subId)
            .mapLeft { DetachAddOnError.SubscriptionNotFound }
            .bind()

        ensure(
            subscription.status is SubscriptionStatus.Active ||
                subscription.status is SubscriptionStatus.Paused,
        ) { DetachAddOnError.InvalidStatus }

        val subscriptionAddOn = subscriptionAddOnCommandQueryPort.findActiveBySubscriptionIdAndAddOnId(subId, aoId)
            .mapLeft { DetachAddOnError.Domain(it) }
            .bind()
            .let { ensureNotNull(it) { DetachAddOnError.AddOnNotAttached } }

        val addOn = addOnQueryPort.findActiveById(aoId)
            .mapLeft { DetachAddOnError.Domain(it) }
            .bind()

        val now = clockPort.now()
        val totalDays = Duration.between(subscription.currentPeriodStart, subscription.currentPeriodEnd).toDays()
        val daysRemaining = calculateDaysRemaining(subscription, now)

        val currency = subscription.plan.basePrice.currency
        val creditAmount = if (addOn != null) {
            if (addOn.billingType == AddOnBillingType.PER_SEAT) {
                addOn.price.multiply(subscriptionAddOn.quantity.toLong() * daysRemaining, totalDays)
            } else {
                addOn.price.multiply(daysRemaining, totalDays)
            }
        } else {
            Money.zero(currency)
        }

        // Generate proration invoice with credit
        val lineItem = InvoiceLineItem(
            description = "Proration credit for detached add-on",
            amount = creditAmount.negate(),
            type = InvoiceLineItemType.ADDON_PRORATION_CREDIT,
        )

        val invoice = Invoice(
            id = null,
            subscriptionId = subscription.id!!,
            lineItems = listOf(lineItem),
            subtotal = creditAmount.negate(),
            discountAmount = Money.zero(currency),
            total = creditAmount.negate(),
            currency = currency,
            status = InvoiceStatus.Open,
            dueDate = now.atOffset(ZoneOffset.UTC).toLocalDate(),
            paidAt = null,
            paymentAttemptCount = 0,
            createdAt = now,
            updatedAt = now,
        )

        transactionPort.run {
            invoiceRepository.save(invoice)
                .mapLeft { DetachAddOnError.Domain(it) }
        }.bind()

        // Add credit to account balance
        if (creditAmount.isPositive()) {
            val newBalance = (subscription.accountCreditBalance + creditAmount)
                .mapLeft { DetachAddOnError.Domain(it) }
                .bind()
            transactionPort.run {
                subscriptionRepository.save(
                    subscription.copy(
                        accountCreditBalance = newBalance,
                        updatedAt = now,
                    ),
                ).mapLeft { DetachAddOnError.Domain(it) }
            }.bind()
        }

        // Detach the add-on
        val detached = subscriptionAddOn.copy(
            status = SubscriptionAddOnStatus.Detached(now),
            detachedAt = now,
        )

        transactionPort.run {
            subscriptionAddOnRepository.save(detached)
                .mapLeft { DetachAddOnError.Domain(it) }
        }.bind()
    }

    private fun calculateDaysRemaining(subscription: Subscription, now: java.time.Instant): Long {
        return when (subscription.status) {
            is SubscriptionStatus.Paused -> {
                // For paused subscriptions, use frozen remaining days
                Duration.between(now, subscription.currentPeriodEnd).toDays().coerceAtLeast(0)
            }
            else -> {
                Duration.between(now, subscription.currentPeriodEnd).toDays().coerceAtLeast(0)
            }
        }
    }
}
