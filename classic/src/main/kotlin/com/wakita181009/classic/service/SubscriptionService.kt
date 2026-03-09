package com.wakita181009.classic.service

import com.wakita181009.classic.dto.CreateSubscriptionRequest
import com.wakita181009.classic.exception.BusinessRuleViolationException
import com.wakita181009.classic.exception.DuplicateSubscriptionException
import com.wakita181009.classic.exception.InvalidStateTransitionException
import com.wakita181009.classic.exception.PaymentFailedException
import com.wakita181009.classic.exception.PlanNotFoundException
import com.wakita181009.classic.exception.SeatCountOutOfRangeException
import com.wakita181009.classic.exception.SeatCountRequiredException
import com.wakita181009.classic.exception.SubscriptionNotFoundException
import com.wakita181009.classic.model.BillingType
import com.wakita181009.classic.model.Discount
import com.wakita181009.classic.model.DiscountType
import com.wakita181009.classic.model.Invoice
import com.wakita181009.classic.model.InvoiceLineItem
import com.wakita181009.classic.model.InvoiceStatus
import com.wakita181009.classic.model.LineItemType
import com.wakita181009.classic.model.Money
import com.wakita181009.classic.model.Subscription
import com.wakita181009.classic.model.SubscriptionAddOnStatus
import com.wakita181009.classic.model.SubscriptionStatus
import com.wakita181009.classic.repository.DiscountRepository
import com.wakita181009.classic.repository.InvoiceRepository
import com.wakita181009.classic.repository.PlanRepository
import com.wakita181009.classic.repository.SubscriptionAddOnRepository
import com.wakita181009.classic.repository.SubscriptionRepository
import com.wakita181009.classic.repository.UsageRecordRepository
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
class SubscriptionService(
    private val subscriptionRepository: SubscriptionRepository,
    private val planRepository: PlanRepository,
    private val invoiceRepository: InvoiceRepository,
    private val usageRecordRepository: UsageRecordRepository,
    private val discountRepository: DiscountRepository,
    private val paymentGateway: PaymentGateway,
    private val clock: Clock,
    private val subscriptionAddOnRepository: SubscriptionAddOnRepository? = null,
) {
    companion object {
        private const val TRIAL_DAYS = 14L
        private const val GRACE_PERIOD_DAYS = 7L
        private const val MAX_PAUSE_COUNT = 2
        private val ACTIVE_STATUSES =
            listOf(
                SubscriptionStatus.TRIAL,
                SubscriptionStatus.ACTIVE,
                SubscriptionStatus.PAUSED,
                SubscriptionStatus.PAST_DUE,
            )
    }

    @Transactional
    fun createSubscription(request: CreateSubscriptionRequest): Subscription {
        val now = Instant.now(clock)
        val plan =
            planRepository.findByIdAndActiveTrue(request.planId)
                ?: throw PlanNotFoundException(request.planId)

        val existing = subscriptionRepository.findByCustomerIdAndStatusIn(request.customerId, ACTIVE_STATUSES)
        if (existing.isNotEmpty()) {
            throw DuplicateSubscriptionException(request.customerId)
        }

        // Phase 1: seat count validation for per-seat plans
        val seatCount: Int?
        if (plan.perSeatPricing) {
            if (request.seatCount == null) {
                throw SeatCountRequiredException()
            }
            if (request.seatCount < plan.minimumSeats) {
                throw SeatCountOutOfRangeException(
                    "Seat count ${request.seatCount} is below minimum ${plan.minimumSeats}",
                )
            }
            plan.maximumSeats?.let { max ->
                if (request.seatCount > max) {
                    throw SeatCountOutOfRangeException(
                        "Seat count ${request.seatCount} exceeds maximum $max",
                    )
                }
            }
            seatCount = request.seatCount
        } else {
            seatCount = null
        }

        val trialEnd = now.plus(Duration.ofDays(TRIAL_DAYS))
        val currency = plan.basePrice.currency
        val subscription =
            Subscription(
                customerId = request.customerId,
                plan = plan,
                status = SubscriptionStatus.TRIAL,
                currentPeriodStart = now,
                currentPeriodEnd = trialEnd,
                trialStart = now,
                trialEnd = trialEnd,
                seatCount = seatCount,
                accountCreditBalance = Money.zero(currency),
                createdAt = now,
                updatedAt = now,
            )

        val saved = subscriptionRepository.save(subscription)

        // Handle discount code
        request.discountCode?.let { code ->
            val discount = resolveDiscount(code, now)
            discount.subscription = saved
            discountRepository.save(discount)
        }

        return saved
    }

    @Transactional
    fun changePlan(
        subscriptionId: Long,
        newPlanId: Long,
    ): Subscription {
        val now = Instant.now(clock)
        val subscription =
            subscriptionRepository.findByIdWithPlan(subscriptionId)
                ?: throw SubscriptionNotFoundException(subscriptionId)

        if (subscription.status != SubscriptionStatus.ACTIVE) {
            throw InvalidStateTransitionException(subscription.status.name, "plan change")
        }

        val newPlan =
            planRepository.findByIdAndActiveTrue(newPlanId)
                ?: throw PlanNotFoundException(newPlanId)

        if (subscription.plan.id == newPlan.id) {
            throw BusinessRuleViolationException("New plan is the same as the current plan")
        }

        if (subscription.plan.basePrice.currency != newPlan.basePrice.currency) {
            throw BusinessRuleViolationException("Cannot change currency during plan change")
        }

        val oldPlan = subscription.plan
        val currency = oldPlan.basePrice.currency
        val periodStart = subscription.currentPeriodStart
        val periodEnd = subscription.currentPeriodEnd
        val totalDays = ChronoUnit.DAYS.between(periodStart, periodEnd)
        val daysRemaining = ChronoUnit.DAYS.between(now, periodEnd)

        // Calculate proration for plan
        val oldPlanCharge =
            if (oldPlan.perSeatPricing && subscription.seatCount != null) {
                oldPlan.basePrice.times(subscription.seatCount!!)
            } else {
                oldPlan.basePrice
            }
        val credit = oldPlanCharge.times(daysRemaining, totalDays)

        val newPlanCharge =
            if (newPlan.perSeatPricing) {
                val seats = subscription.seatCount ?: newPlan.minimumSeats
                newPlan.basePrice.times(seats)
            } else {
                newPlan.basePrice
            }
        val charge = newPlanCharge.times(daysRemaining, totalDays)
        val netProration = charge - credit

        // Create proration invoice (NO discount applied to proration)
        val zeroCurrency = Money.zero(currency)
        val invoice =
            Invoice(
                subscription = subscription,
                subtotal = netProration,
                discountAmount = zeroCurrency,
                total = netProration,
                status = InvoiceStatus.DRAFT,
                dueDate = LocalDate.ofInstant(now, ZoneOffset.UTC),
                createdAt = now,
                updatedAt = now,
            )

        val creditLineItem =
            InvoiceLineItem(
                invoice = invoice,
                description = "Proration credit for ${oldPlan.name}",
                amount = credit.negate(),
                type = LineItemType.PRORATION_CREDIT,
            )
        val chargeLineItem =
            InvoiceLineItem(
                invoice = invoice,
                description = "Proration charge for ${newPlan.name}",
                amount = charge,
                type = LineItemType.PRORATION_CHARGE,
            )
        invoice.addLineItem(creditLineItem)
        invoice.addLineItem(chargeLineItem)

        // Phase 1: Handle add-on incompatibilities
        val addOnCredits = mutableListOf<Money>()
        subscriptionAddOnRepository?.let { saRepo ->
            val activeAddOns = saRepo.findBySubscriptionIdAndStatus(subscriptionId, SubscriptionAddOnStatus.ACTIVE)
            for (sa in activeAddOns) {
                val addOn = sa.addOn
                var shouldDetach = false

                // Check tier compatibility
                if (newPlan.tier !in addOn.compatibleTiers) {
                    shouldDetach = true
                }

                // Check PER_SEAT add-on on non-per-seat plan
                if (addOn.billingType == BillingType.PER_SEAT && !newPlan.perSeatPricing) {
                    shouldDetach = true
                }

                if (shouldDetach) {
                    // Calculate proration credit for detached add-on
                    val addOnCredit = addOn.price.times(sa.quantity).times(daysRemaining, totalDays)
                    addOnCredits.add(addOnCredit)

                    val addOnCreditItem =
                        InvoiceLineItem(
                            invoice = invoice,
                            description = "Proration credit for detached add-on: ${addOn.name}",
                            amount = addOnCredit.negate(),
                            type = LineItemType.ADDON_PRORATION_CREDIT,
                        )
                    invoice.addLineItem(addOnCreditItem)

                    // Detach
                    sa.transitionTo(SubscriptionAddOnStatus.DETACHED)
                    sa.detachedAt = now
                    saRepo.save(sa)
                }
            }
        }

        // Update invoice totals if add-on credits were added
        if (addOnCredits.isNotEmpty()) {
            var totalAddonCredit = Money.zero(currency)
            for (ac in addOnCredits) {
                totalAddonCredit = totalAddonCredit + ac
            }
            val adjustedTotal = netProration - totalAddonCredit
            invoice.subtotal = adjustedTotal
            invoice.total = adjustedTotal

            // Add add-on credits to account balance
            subscription.accountCreditBalance = subscription.accountCreditBalance + totalAddonCredit
        }

        invoice.transitionTo(InvoiceStatus.OPEN)

        val isUpgrade = oldPlan.tier.isUpgradeTo(newPlan.tier)

        if (isUpgrade && invoice.total.amount > BigDecimal.ZERO) {
            // Charge immediately for upgrades
            val result = paymentGateway.charge(invoice.total, "CREDIT_CARD", subscription.customerId)
            if (!result.success) {
                invoiceRepository.save(invoice)
                throw PaymentFailedException("Payment failed: ${result.errorReason}")
            }
            invoice.transitionTo(InvoiceStatus.PAID)
            invoice.paidAt = now
            invoice.paymentAttemptCount = 1
        }

        invoiceRepository.save(invoice)

        // Phase 1: Handle per-seat transitions
        if (oldPlan.perSeatPricing && !newPlan.perSeatPricing) {
            // Per-seat to non-per-seat: set seatCount to null
            subscription.seatCount = null
        } else if (!oldPlan.perSeatPricing && newPlan.perSeatPricing) {
            // Non-per-seat to per-seat: set seatCount to newPlan's minimumSeats
            subscription.seatCount = newPlan.minimumSeats
        }

        subscription.plan = newPlan
        subscription.updatedAt = now
        return subscriptionRepository.save(subscription)
    }

    @Transactional
    fun pauseSubscription(subscriptionId: Long): Subscription {
        val now = Instant.now(clock)
        val subscription =
            subscriptionRepository.findByIdWithPlan(subscriptionId)
                ?: throw SubscriptionNotFoundException(subscriptionId)

        if (subscription.status != SubscriptionStatus.ACTIVE) {
            throw InvalidStateTransitionException(subscription.status.name, "PAUSED")
        }

        if (subscription.pauseCount >= MAX_PAUSE_COUNT) {
            throw BusinessRuleViolationException("Pause limit of $MAX_PAUSE_COUNT reached for this billing period")
        }

        subscription.transitionTo(SubscriptionStatus.PAUSED)
        subscription.pausedAt = now
        subscription.pauseCount++
        subscription.updatedAt = now

        return subscriptionRepository.save(subscription)
    }

    @Transactional
    fun resumeSubscription(subscriptionId: Long): Subscription {
        val now = Instant.now(clock)
        val subscription =
            subscriptionRepository.findByIdWithPlan(subscriptionId)
                ?: throw SubscriptionNotFoundException(subscriptionId)

        if (subscription.status != SubscriptionStatus.PAUSED) {
            throw InvalidStateTransitionException(subscription.status.name, "ACTIVE (resume)")
        }

        val pausedAt = subscription.pausedAt!!
        val remainingDays = Duration.between(pausedAt, subscription.currentPeriodEnd)

        subscription.transitionTo(SubscriptionStatus.ACTIVE)
        subscription.currentPeriodStart = now
        subscription.currentPeriodEnd = now.plus(remainingDays)
        subscription.pausedAt = null
        subscription.updatedAt = now

        return subscriptionRepository.save(subscription)
    }

    @Transactional
    fun cancelSubscription(
        subscriptionId: Long,
        immediate: Boolean,
    ): Subscription {
        val now = Instant.now(clock)
        val subscription =
            subscriptionRepository.findByIdWithPlan(subscriptionId)
                ?: throw SubscriptionNotFoundException(subscriptionId)

        if (subscription.status == SubscriptionStatus.CANCELED ||
            subscription.status == SubscriptionStatus.EXPIRED
        ) {
            throw InvalidStateTransitionException(subscription.status.name, "CANCELED")
        }

        if (!immediate) {
            // End-of-period cancellation
            if (subscription.status == SubscriptionStatus.PAUSED) {
                throw BusinessRuleViolationException("Paused subscriptions can only be canceled immediately")
            }
            if (subscription.status != SubscriptionStatus.ACTIVE &&
                subscription.status != SubscriptionStatus.PAST_DUE
            ) {
                throw InvalidStateTransitionException(subscription.status.name, "cancel at period end")
            }
            subscription.cancelAtPeriodEnd = true
            subscription.canceledAt = now
            subscription.updatedAt = now
            return subscriptionRepository.save(subscription)
        }

        // Immediate cancellation
        subscription.transitionTo(SubscriptionStatus.CANCELED)
        subscription.canceledAt = now
        subscription.updatedAt = now

        // Void open invoices
        val openInvoices = invoiceRepository.findBySubscriptionIdAndStatus(subscription.id, InvoiceStatus.OPEN)
        openInvoices.forEach { invoice ->
            invoice.transitionTo(InvoiceStatus.VOID)
            invoice.updatedAt = now
            invoiceRepository.save(invoice)
        }

        return subscriptionRepository.save(subscription)
    }

    @Transactional
    fun processRenewal(subscriptionId: Long): Subscription {
        val now = Instant.now(clock)
        val subscription =
            subscriptionRepository.findByIdWithPlan(subscriptionId)
                ?: throw SubscriptionNotFoundException(subscriptionId)

        // Skip conditions
        if (subscription.status != SubscriptionStatus.ACTIVE) {
            return subscription
        }
        if (subscription.currentPeriodEnd.isAfter(now)) {
            return subscription
        }

        // Check cancel at period end
        if (subscription.cancelAtPeriodEnd) {
            subscription.transitionTo(SubscriptionStatus.CANCELED)
            subscription.updatedAt = now
            return subscriptionRepository.save(subscription)
        }

        val plan = subscription.plan
        val currency = plan.basePrice.currency
        val periodStart = subscription.currentPeriodStart
        val periodEnd = subscription.currentPeriodEnd

        // Phase 1: Per-seat pricing for plan charge
        val planChargeAmount =
            if (plan.perSeatPricing && subscription.seatCount != null) {
                plan.basePrice.times(subscription.seatCount!!)
            } else {
                plan.basePrice
            }

        var subtotal = planChargeAmount

        // Usage charges
        val usageRecords =
            usageRecordRepository
                .findBySubscriptionIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThan(
                    subscription.id,
                    periodStart,
                    periodEnd,
                )
        if (usageRecords.isNotEmpty()) {
            val totalUsage = usageRecords.sumOf { it.quantity }
            val usageCharge =
                Money(
                    BigDecimal(totalUsage).multiply(BigDecimal("0.01")).setScale(currency.scale, java.math.RoundingMode.HALF_UP),
                    currency,
                )
            subtotal = subtotal + usageCharge
        }

        // Phase 1: Add-on charges
        val activeAddOns =
            subscriptionAddOnRepository?.findBySubscriptionIdAndStatus(
                subscription.id,
                SubscriptionAddOnStatus.ACTIVE,
            ) ?: emptyList()

        for (sa in activeAddOns) {
            val addOnCharge =
                when (sa.addOn.billingType) {
                    BillingType.FLAT -> sa.addOn.price
                    BillingType.PER_SEAT -> sa.addOn.price.times(sa.quantity)
                }
            subtotal = subtotal + addOnCharge
        }

        // Apply discount
        var discountAmount = Money.zero(currency)
        val discount = discountRepository.findBySubscriptionId(subscription.id)
        if (discount != null && (discount.remainingCycles == null || discount.remainingCycles!! > 0)) {
            discountAmount =
                when (discount.type) {
                    DiscountType.PERCENTAGE -> {
                        subtotal.times(discount.value.toLong(), 100L)
                    }
                    DiscountType.FIXED_AMOUNT -> {
                        val fixedAmount = Money(discount.value.setScale(currency.scale, java.math.RoundingMode.HALF_UP), currency)
                        if (fixedAmount.amount > subtotal.amount) subtotal else fixedAmount
                    }
                }
            // Decrement remaining cycles
            discount.remainingCycles?.let {
                discount.remainingCycles = it - 1
                discountRepository.save(discount)
            }
        }

        val afterDiscount = subtotal - discountAmount
        val afterDiscountClamped = if (afterDiscount.amount < BigDecimal.ZERO) Money.zero(currency) else afterDiscount

        // Phase 1: Apply account credit
        var accountCreditApplied = Money.zero(currency)
        if (afterDiscountClamped.amount > BigDecimal.ZERO &&
            subscription.accountCreditBalance.amount > BigDecimal.ZERO
        ) {
            val creditToApply =
                if (subscription.accountCreditBalance.amount >= afterDiscountClamped.amount) {
                    afterDiscountClamped
                } else {
                    subscription.accountCreditBalance
                }
            accountCreditApplied = creditToApply
            subscription.accountCreditBalance = subscription.accountCreditBalance - creditToApply
        }

        val finalTotal = afterDiscountClamped - accountCreditApplied
        val finalTotalClamped = if (finalTotal.amount < BigDecimal.ZERO) Money.zero(currency) else finalTotal

        val invoice =
            Invoice(
                subscription = subscription,
                subtotal = subtotal,
                discountAmount = discountAmount,
                total = finalTotalClamped,
                status = InvoiceStatus.DRAFT,
                dueDate = LocalDate.ofInstant(periodEnd, ZoneOffset.UTC),
                createdAt = now,
                updatedAt = now,
            )

        // Add plan charge line item
        val planChargeItem =
            InvoiceLineItem(
                invoice = invoice,
                description = "${plan.name} Plan - ${plan.billingInterval}",
                amount = planChargeAmount,
                type = LineItemType.PLAN_CHARGE,
            )
        invoice.addLineItem(planChargeItem)

        // Add usage line items
        if (usageRecords.isNotEmpty()) {
            val totalUsage = usageRecords.sumOf { it.quantity }
            val usageCharge =
                Money(
                    BigDecimal(totalUsage).multiply(BigDecimal("0.01")).setScale(currency.scale, java.math.RoundingMode.HALF_UP),
                    currency,
                )
            val usageItem =
                InvoiceLineItem(
                    invoice = invoice,
                    description = "Usage charges: $totalUsage units",
                    amount = usageCharge,
                    type = LineItemType.USAGE_CHARGE,
                )
            invoice.addLineItem(usageItem)
        }

        // Phase 1: Add add-on charge line items
        for (sa in activeAddOns) {
            val addOnCharge =
                when (sa.addOn.billingType) {
                    BillingType.FLAT -> sa.addOn.price
                    BillingType.PER_SEAT -> sa.addOn.price.times(sa.quantity)
                }
            val addOnItem =
                InvoiceLineItem(
                    invoice = invoice,
                    description = "Add-on: ${sa.addOn.name}",
                    amount = addOnCharge,
                    type = LineItemType.ADDON_CHARGE,
                )
            invoice.addLineItem(addOnItem)
        }

        // Phase 1: Add account credit line item
        if (accountCreditApplied.amount > BigDecimal.ZERO) {
            val creditItem =
                InvoiceLineItem(
                    invoice = invoice,
                    description = "Account credit applied",
                    amount = accountCreditApplied.negate(),
                    type = LineItemType.ACCOUNT_CREDIT,
                )
            invoice.addLineItem(creditItem)
        }

        invoice.transitionTo(InvoiceStatus.OPEN)

        if (finalTotalClamped.amount.compareTo(BigDecimal.ZERO) == 0) {
            // Zero total -> auto-Paid
            invoice.transitionTo(InvoiceStatus.PAID)
            invoice.paidAt = now
            invoiceRepository.save(invoice)
        } else {
            // Attempt payment
            val result = paymentGateway.charge(finalTotalClamped, "CREDIT_CARD", subscription.customerId)
            invoice.paymentAttemptCount = 1
            if (result.success) {
                invoice.transitionTo(InvoiceStatus.PAID)
                invoice.paidAt = now
                invoiceRepository.save(invoice)
            } else {
                // Payment failed -> PastDue
                invoiceRepository.save(invoice)
                subscription.transitionTo(SubscriptionStatus.PAST_DUE)
                subscription.gracePeriodEnd = now.plus(Duration.ofDays(GRACE_PERIOD_DAYS))
                subscription.updatedAt = now
                return subscriptionRepository.save(subscription)
            }
        }

        // Advance billing period
        val newPeriodStart = periodEnd
        val newPeriodEnd = plan.billingInterval.addTo(periodEnd)
        subscription.currentPeriodStart = newPeriodStart
        subscription.currentPeriodEnd = newPeriodEnd
        subscription.pauseCount = 0
        subscription.updatedAt = now

        return subscriptionRepository.save(subscription)
    }

    @Transactional(readOnly = true)
    fun getSubscription(id: Long): Subscription =
        subscriptionRepository.findByIdWithPlan(id)
            ?: throw SubscriptionNotFoundException(id)

    @Transactional(readOnly = true)
    fun listByCustomerId(customerId: Long): List<Subscription> = subscriptionRepository.findByCustomerId(customerId)

    private fun resolveDiscount(
        code: String,
        now: Instant,
    ): Discount {
        // Simple discount code resolution
        return when (code) {
            "WELCOME20" ->
                Discount(
                    type = DiscountType.PERCENTAGE,
                    value = BigDecimal("20"),
                    durationMonths = 3,
                    remainingCycles = 3,
                    appliedAt = now,
                )
            else ->
                throw com.wakita181009.classic.exception
                    .InvalidDiscountCodeException(code)
        }
    }
}
