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
import com.wakita181009.classic.model.BillingType
import com.wakita181009.classic.model.Invoice
import com.wakita181009.classic.model.InvoiceLineItem
import com.wakita181009.classic.model.InvoiceStatus
import com.wakita181009.classic.model.LineItemType
import com.wakita181009.classic.model.Money
import com.wakita181009.classic.model.SubscriptionAddOn
import com.wakita181009.classic.model.SubscriptionAddOnStatus
import com.wakita181009.classic.model.SubscriptionStatus
import com.wakita181009.classic.repository.AddOnRepository
import com.wakita181009.classic.repository.InvoiceRepository
import com.wakita181009.classic.repository.SubscriptionAddOnRepository
import com.wakita181009.classic.repository.SubscriptionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

@Service
class AddOnService(
    private val subscriptionRepository: SubscriptionRepository,
    private val addOnRepository: AddOnRepository,
    private val subscriptionAddOnRepository: SubscriptionAddOnRepository,
    private val invoiceRepository: InvoiceRepository,
    private val paymentGateway: PaymentGateway,
    private val clock: Clock,
) {
    companion object {
        private const val MAX_ACTIVE_ADDONS = 5
    }

    @Transactional
    fun attachAddOn(
        subscriptionId: Long,
        addOnId: Long,
    ): SubscriptionAddOn {
        val now = Instant.now(clock)
        val subscription =
            subscriptionRepository.findByIdWithPlan(subscriptionId)
                ?: throw SubscriptionNotFoundException(subscriptionId)

        if (subscription.status != SubscriptionStatus.ACTIVE) {
            throw InvalidStateTransitionException(subscription.status.name, "attach add-on (requires ACTIVE)")
        }

        val addOn =
            addOnRepository.findByIdAndActiveTrue(addOnId)
                ?: throw AddOnNotFoundException(addOnId)

        // Currency match
        if (addOn.currency != subscription.plan.basePrice.currency) {
            throw CurrencyMismatchException(
                subscription.plan.basePrice.currency.name,
                addOn.currency.name,
            )
        }

        // Tier compatibility
        if (subscription.plan.tier !in addOn.compatibleTiers) {
            throw TierIncompatibilityException(
                subscription.plan.tier.name,
                addOn.compatibleTiers.joinToString(", ") { it.name },
            )
        }

        // PER_SEAT check
        if (addOn.billingType == BillingType.PER_SEAT && !subscription.plan.perSeatPricing) {
            throw PerSeatAddOnOnNonPerSeatPlanException()
        }

        // Duplicate check
        val existing =
            subscriptionAddOnRepository.findBySubscriptionIdAndAddOnIdAndStatus(
                subscriptionId,
                addOnId,
                SubscriptionAddOnStatus.ACTIVE,
            )
        if (existing != null) {
            throw DuplicateAddOnException(addOnId)
        }

        // Limit check
        val activeCount = subscriptionAddOnRepository.countBySubscriptionIdAndStatus(subscriptionId, SubscriptionAddOnStatus.ACTIVE)
        if (activeCount >= MAX_ACTIVE_ADDONS) {
            throw AddOnLimitReachedException(MAX_ACTIVE_ADDONS)
        }

        // Calculate proration
        val periodStart = subscription.currentPeriodStart
        val periodEnd = subscription.currentPeriodEnd
        val totalDays = ChronoUnit.DAYS.between(periodStart, periodEnd)
        val daysRemaining = ChronoUnit.DAYS.between(now, periodEnd)

        val quantity =
            when (addOn.billingType) {
                BillingType.FLAT -> 1
                BillingType.PER_SEAT -> subscription.seatCount ?: 1
            }

        val baseCharge = addOn.price.times(quantity)
        val proratedCharge = baseCharge.times(daysRemaining, totalDays)

        // Create proration invoice
        val currency = subscription.plan.basePrice.currency
        val zeroCurrency = Money.zero(currency)
        val invoice =
            Invoice(
                subscription = subscription,
                subtotal = proratedCharge,
                discountAmount = zeroCurrency,
                total = proratedCharge,
                status = InvoiceStatus.DRAFT,
                dueDate = LocalDate.ofInstant(now, ZoneOffset.UTC),
                createdAt = now,
                updatedAt = now,
            )

        val lineItem =
            InvoiceLineItem(
                invoice = invoice,
                description = "Proration charge for add-on: ${addOn.name}",
                amount = proratedCharge,
                type = LineItemType.ADDON_PRORATION_CHARGE,
            )
        invoice.addLineItem(lineItem)
        invoice.transitionTo(InvoiceStatus.OPEN)

        // Charge via payment gateway
        val result = paymentGateway.charge(proratedCharge, "CREDIT_CARD", subscription.customerId)
        if (!result.success) {
            invoiceRepository.save(invoice)
            throw PaymentFailedException("Payment failed for add-on attachment: ${result.errorReason}")
        }

        invoice.transitionTo(InvoiceStatus.PAID)
        invoice.paidAt = now
        invoice.paymentAttemptCount = 1
        invoiceRepository.save(invoice)

        // Create SubscriptionAddOn
        val subscriptionAddOn =
            SubscriptionAddOn(
                subscription = subscription,
                addOn = addOn,
                quantity = quantity,
                status = SubscriptionAddOnStatus.ACTIVE,
                attachedAt = now,
            )

        return subscriptionAddOnRepository.save(subscriptionAddOn)
    }

    @Transactional
    fun detachAddOn(
        subscriptionId: Long,
        addOnId: Long,
    ): SubscriptionAddOn {
        val now = Instant.now(clock)
        val subscription =
            subscriptionRepository.findByIdWithPlan(subscriptionId)
                ?: throw SubscriptionNotFoundException(subscriptionId)

        if (subscription.status != SubscriptionStatus.ACTIVE &&
            subscription.status != SubscriptionStatus.PAUSED
        ) {
            throw InvalidStateTransitionException(subscription.status.name, "detach add-on (requires ACTIVE or PAUSED)")
        }

        val subscriptionAddOn =
            subscriptionAddOnRepository.findBySubscriptionIdAndAddOnIdAndStatus(
                subscriptionId,
                addOnId,
                SubscriptionAddOnStatus.ACTIVE,
            ) ?: throw SubscriptionAddOnNotFoundException(subscriptionId, addOnId)

        val addOn = subscriptionAddOn.addOn

        // Calculate proration credit
        val periodStart = subscription.currentPeriodStart
        val periodEnd = subscription.currentPeriodEnd
        val totalDays = ChronoUnit.DAYS.between(periodStart, periodEnd)

        val daysRemaining =
            if (subscription.status == SubscriptionStatus.PAUSED && subscription.pausedAt != null) {
                // For paused subscriptions, use frozen remaining days
                Duration.between(subscription.pausedAt, subscription.currentPeriodEnd).toDays()
            } else {
                ChronoUnit.DAYS.between(now, periodEnd)
            }

        val baseCredit = addOn.price.times(subscriptionAddOn.quantity)
        val proratedCredit = baseCredit.times(daysRemaining, totalDays)

        // Create proration invoice with negative amount
        val currency = subscription.plan.basePrice.currency
        val zeroCurrency = Money.zero(currency)
        val negativeCredit = proratedCredit.negate()
        val invoice =
            Invoice(
                subscription = subscription,
                subtotal = negativeCredit,
                discountAmount = zeroCurrency,
                total = negativeCredit,
                status = InvoiceStatus.DRAFT,
                dueDate = LocalDate.ofInstant(now, ZoneOffset.UTC),
                createdAt = now,
                updatedAt = now,
            )

        val lineItem =
            InvoiceLineItem(
                invoice = invoice,
                description = "Proration credit for detached add-on: ${addOn.name}",
                amount = negativeCredit,
                type = LineItemType.ADDON_PRORATION_CREDIT,
            )
        invoice.addLineItem(lineItem)
        invoice.transitionTo(InvoiceStatus.OPEN)
        invoice.transitionTo(InvoiceStatus.PAID)
        invoice.paidAt = now
        invoiceRepository.save(invoice)

        // Add credit to account balance
        if (proratedCredit.amount > BigDecimal.ZERO) {
            subscription.accountCreditBalance = subscription.accountCreditBalance + proratedCredit
            subscription.updatedAt = now
            subscriptionRepository.save(subscription)
        }

        // Detach
        subscriptionAddOn.transitionTo(SubscriptionAddOnStatus.DETACHED)
        subscriptionAddOn.detachedAt = now

        return subscriptionAddOnRepository.save(subscriptionAddOn)
    }

    @Transactional(readOnly = true)
    fun listAddOns(subscriptionId: Long): List<SubscriptionAddOn> = subscriptionAddOnRepository.findBySubscriptionId(subscriptionId)
}
