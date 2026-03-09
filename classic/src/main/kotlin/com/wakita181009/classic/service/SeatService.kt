package com.wakita181009.classic.service

import com.wakita181009.classic.exception.InvalidStateTransitionException
import com.wakita181009.classic.exception.NotPerSeatPlanException
import com.wakita181009.classic.exception.PaymentFailedException
import com.wakita181009.classic.exception.SameSeatCountException
import com.wakita181009.classic.exception.SeatCountOutOfRangeException
import com.wakita181009.classic.exception.SubscriptionNotFoundException
import com.wakita181009.classic.model.BillingType
import com.wakita181009.classic.model.Invoice
import com.wakita181009.classic.model.InvoiceLineItem
import com.wakita181009.classic.model.InvoiceStatus
import com.wakita181009.classic.model.LineItemType
import com.wakita181009.classic.model.Money
import com.wakita181009.classic.model.Subscription
import com.wakita181009.classic.model.SubscriptionAddOnStatus
import com.wakita181009.classic.model.SubscriptionStatus
import com.wakita181009.classic.repository.InvoiceRepository
import com.wakita181009.classic.repository.SubscriptionAddOnRepository
import com.wakita181009.classic.repository.SubscriptionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.abs

@Service
class SeatService(
    private val subscriptionRepository: SubscriptionRepository,
    private val subscriptionAddOnRepository: SubscriptionAddOnRepository,
    private val invoiceRepository: InvoiceRepository,
    private val paymentGateway: PaymentGateway,
    private val clock: Clock,
) {
    @Transactional
    fun updateSeatCount(
        subscriptionId: Long,
        newSeatCount: Int,
    ): Subscription {
        val now = Instant.now(clock)
        val subscription =
            subscriptionRepository.findByIdWithPlan(subscriptionId)
                ?: throw SubscriptionNotFoundException(subscriptionId)

        if (subscription.status != SubscriptionStatus.ACTIVE) {
            throw InvalidStateTransitionException(subscription.status.name, "update seats (requires ACTIVE)")
        }

        val plan = subscription.plan
        if (!plan.perSeatPricing) {
            throw NotPerSeatPlanException()
        }

        val currentSeatCount = subscription.seatCount ?: plan.minimumSeats
        if (newSeatCount == currentSeatCount) {
            throw SameSeatCountException(currentSeatCount)
        }

        if (newSeatCount < plan.minimumSeats) {
            throw SeatCountOutOfRangeException("Seat count $newSeatCount is below minimum ${plan.minimumSeats}")
        }

        plan.maximumSeats?.let { max ->
            if (newSeatCount > max) {
                throw SeatCountOutOfRangeException("Seat count $newSeatCount exceeds maximum $max")
            }
        }

        val periodStart = subscription.currentPeriodStart
        val periodEnd = subscription.currentPeriodEnd
        val totalDays = ChronoUnit.DAYS.between(periodStart, periodEnd)
        val daysRemaining = ChronoUnit.DAYS.between(now, periodEnd)
        val seatDifference = newSeatCount - currentSeatCount
        val absSeatDifference = abs(seatDifference)

        val currency = plan.basePrice.currency
        val zeroCurrency = Money.zero(currency)

        // Calculate seat proration
        val seatProrationAmount = plan.basePrice.times(absSeatDifference).times(daysRemaining, totalDays)

        // Calculate PER_SEAT add-on prorations
        val activePerSeatAddOns =
            subscriptionAddOnRepository
                .findBySubscriptionIdAndStatus(subscriptionId, SubscriptionAddOnStatus.ACTIVE)
                .filter { it.addOn.billingType == BillingType.PER_SEAT }

        val isIncrease = seatDifference > 0

        if (isIncrease) {
            // Seat increase: charge immediately
            val invoice =
                Invoice(
                    subscription = subscription,
                    subtotal = seatProrationAmount,
                    discountAmount = zeroCurrency,
                    total = seatProrationAmount,
                    status = InvoiceStatus.DRAFT,
                    dueDate = LocalDate.ofInstant(now, ZoneOffset.UTC),
                    createdAt = now,
                    updatedAt = now,
                )

            val seatLineItem =
                InvoiceLineItem(
                    invoice = invoice,
                    description = "Proration charge for $seatDifference additional seat(s)",
                    amount = seatProrationAmount,
                    type = LineItemType.SEAT_PRORATION_CHARGE,
                )
            invoice.addLineItem(seatLineItem)

            // Add-on prorations for seat increase
            var totalAddonProration = zeroCurrency
            for (sa in activePerSeatAddOns) {
                val addonProration =
                    sa.addOn.price
                        .times(absSeatDifference)
                        .times(daysRemaining, totalDays)
                val addonLineItem =
                    InvoiceLineItem(
                        invoice = invoice,
                        description = "Proration charge for add-on ${sa.addOn.name} (+$seatDifference seats)",
                        amount = addonProration,
                        type = LineItemType.ADDON_PRORATION_CHARGE,
                    )
                invoice.addLineItem(addonLineItem)
                totalAddonProration = totalAddonProration + addonProration
            }

            val invoiceTotal = seatProrationAmount + totalAddonProration
            invoice.subtotal = invoiceTotal
            invoice.total = invoiceTotal

            invoice.transitionTo(InvoiceStatus.OPEN)

            val result = paymentGateway.charge(invoiceTotal, "CREDIT_CARD", subscription.customerId)
            if (!result.success) {
                invoiceRepository.save(invoice)
                throw PaymentFailedException("Payment failed for seat increase: ${result.errorReason}")
            }

            invoice.transitionTo(InvoiceStatus.PAID)
            invoice.paidAt = now
            invoice.paymentAttemptCount = 1
            invoiceRepository.save(invoice)
        } else {
            // Seat decrease: credit to account balance
            val invoice =
                Invoice(
                    subscription = subscription,
                    subtotal = seatProrationAmount.negate(),
                    discountAmount = zeroCurrency,
                    total = seatProrationAmount.negate(),
                    status = InvoiceStatus.DRAFT,
                    dueDate = LocalDate.ofInstant(now, ZoneOffset.UTC),
                    createdAt = now,
                    updatedAt = now,
                )

            val seatLineItem =
                InvoiceLineItem(
                    invoice = invoice,
                    description = "Proration credit for $absSeatDifference reduced seat(s)",
                    amount = seatProrationAmount.negate(),
                    type = LineItemType.SEAT_PRORATION_CREDIT,
                )
            invoice.addLineItem(seatLineItem)

            var totalCredit = seatProrationAmount

            // Add-on prorations for seat decrease
            for (sa in activePerSeatAddOns) {
                val addonCredit =
                    sa.addOn.price
                        .times(absSeatDifference)
                        .times(daysRemaining, totalDays)
                val addonLineItem =
                    InvoiceLineItem(
                        invoice = invoice,
                        description = "Proration credit for add-on ${sa.addOn.name} (-$absSeatDifference seats)",
                        amount = addonCredit.negate(),
                        type = LineItemType.ADDON_PRORATION_CREDIT,
                    )
                invoice.addLineItem(addonLineItem)
                totalCredit = totalCredit + addonCredit
            }

            val invoiceTotal = totalCredit.negate()
            invoice.subtotal = invoiceTotal
            invoice.total = invoiceTotal

            invoice.transitionTo(InvoiceStatus.OPEN)
            invoice.transitionTo(InvoiceStatus.PAID)
            invoice.paidAt = now
            invoiceRepository.save(invoice)

            // Add credit to account balance
            subscription.accountCreditBalance = subscription.accountCreditBalance + totalCredit
        }

        // Update seat count
        subscription.seatCount = newSeatCount

        // Update PER_SEAT add-on quantities
        for (sa in activePerSeatAddOns) {
            sa.quantity = newSeatCount
            subscriptionAddOnRepository.save(sa)
        }

        // Also update FLAT add-ons (they don't change quantity but we fetched only PER_SEAT above)

        subscription.updatedAt = now
        return subscriptionRepository.save(subscription)
    }
}
