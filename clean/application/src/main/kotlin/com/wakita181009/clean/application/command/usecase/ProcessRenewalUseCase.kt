package com.wakita181009.clean.application.command.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.wakita181009.clean.application.command.error.ProcessRenewalError
import com.wakita181009.clean.application.command.port.AddOnQueryPort
import com.wakita181009.clean.application.command.port.PaymentGatewayPort
import com.wakita181009.clean.application.command.port.SubscriptionAddOnCommandQueryPort
import com.wakita181009.clean.application.command.port.SubscriptionCommandQueryPort
import com.wakita181009.clean.application.command.port.UsageQueryPort
import com.wakita181009.clean.application.port.ClockPort
import com.wakita181009.clean.application.port.TransactionPort
import com.wakita181009.clean.domain.model.AddOnBillingType
import com.wakita181009.clean.domain.model.Discount
import com.wakita181009.clean.domain.model.Invoice
import com.wakita181009.clean.domain.model.InvoiceLineItem
import com.wakita181009.clean.domain.model.InvoiceLineItemType
import com.wakita181009.clean.domain.model.InvoiceStatus
import com.wakita181009.clean.domain.model.Money
import com.wakita181009.clean.domain.model.Subscription
import com.wakita181009.clean.domain.model.SubscriptionId
import com.wakita181009.clean.domain.model.SubscriptionStatus
import com.wakita181009.clean.domain.repository.DiscountRepository
import com.wakita181009.clean.domain.repository.InvoiceRepository
import com.wakita181009.clean.domain.repository.SubscriptionRepository
import java.time.Duration
import java.time.ZoneOffset

interface ProcessRenewalUseCase {
    fun execute(subscriptionId: Long): Either<ProcessRenewalError, Subscription>
}

class ProcessRenewalUseCaseImpl(
    private val subscriptionCommandQueryPort: SubscriptionCommandQueryPort,
    private val usageQueryPort: UsageQueryPort,
    private val paymentGatewayPort: PaymentGatewayPort,
    private val subscriptionRepository: SubscriptionRepository,
    private val invoiceRepository: InvoiceRepository,
    private val discountRepository: DiscountRepository,
    private val clockPort: ClockPort,
    private val transactionPort: TransactionPort,
    private val subscriptionAddOnCommandQueryPort: SubscriptionAddOnCommandQueryPort? = null,
    private val addOnQueryPort: AddOnQueryPort? = null,
) : ProcessRenewalUseCase {

    override fun execute(subscriptionId: Long): Either<ProcessRenewalError, Subscription> = either {
        val subId = SubscriptionId(subscriptionId)
            .mapLeft { ProcessRenewalError.Internal(it.message) }
            .bind()

        val (subscription, discount) = subscriptionCommandQueryPort.findByIdWithDiscount(subId)
            .mapLeft { ProcessRenewalError.SubscriptionNotFound }
            .bind()

        val now = clockPort.now()
        ensure(subscription.status is SubscriptionStatus.Active) { ProcessRenewalError.NotDue }
        ensure(!subscription.currentPeriodEnd.isAfter(now)) { ProcessRenewalError.NotDue }

        // Check if cancel at period end
        if (subscription.cancelAtPeriodEnd) {
            val canceled = transactionPort.run {
                subscriptionRepository.save(
                    subscription.copy(
                        status = SubscriptionStatus.Canceled(now),
                        canceledAt = subscription.canceledAt ?: now,
                        updatedAt = now,
                    ),
                ).mapLeft { ProcessRenewalError.Domain(it) }
            }.bind()
            return@either canceled
        }

        val currency = subscription.plan.basePrice.currency
        val lineItems = mutableListOf<InvoiceLineItem>()

        // Plan charge - for per-seat: base_price * seat_count
        val planChargeAmount = if (subscription.plan.perSeatPricing && subscription.seatCount != null) {
            subscription.plan.basePrice.multiply(subscription.seatCount!!.toLong(), 1L)
        } else {
            subscription.plan.basePrice
        }

        lineItems.add(
            InvoiceLineItem(
                description = "${subscription.plan.name} - ${subscription.plan.billingInterval}",
                amount = planChargeAmount,
                type = InvoiceLineItemType.PLAN_CHARGE,
            ),
        )

        // Add-on charges
        if (subscriptionAddOnCommandQueryPort != null && addOnQueryPort != null) {
            val activeAddOns = subscriptionAddOnCommandQueryPort.findActiveBySubscriptionId(subId)
                .mapLeft { ProcessRenewalError.Domain(it) }
                .bind()

            for (subscriptionAddOn in activeAddOns) {
                val addOn = addOnQueryPort.findActiveById(subscriptionAddOn.addOnId)
                    .mapLeft { ProcessRenewalError.Domain(it) }
                    .bind()

                if (addOn != null) {
                    val addonChargeAmount = if (addOn.billingType == AddOnBillingType.PER_SEAT) {
                        addOn.price.multiply(subscriptionAddOn.quantity.toLong(), 1L)
                    } else {
                        addOn.price
                    }

                    lineItems.add(
                        InvoiceLineItem(
                            description = "Add-on: ${addOn.name}",
                            amount = addonChargeAmount,
                            type = InvoiceLineItemType.ADDON_CHARGE,
                        ),
                    )
                }
            }
        }

        // Usage charges
        val usageRecords = usageQueryPort.findForPeriod(
            subscription.id!!,
            subscription.currentPeriodStart,
            subscription.currentPeriodEnd,
        ).mapLeft { ProcessRenewalError.Domain(it) }.bind()

        if (usageRecords.isNotEmpty()) {
            val totalQuantity = usageRecords.sumOf { it.quantity }
            val usageAmount = Money.zero(currency)
            if (usageAmount.isPositive()) {
                lineItems.add(
                    InvoiceLineItem(
                        description = "Usage charges: $totalQuantity units",
                        amount = usageAmount,
                        type = InvoiceLineItemType.USAGE_CHARGE,
                    ),
                )
            }
        }

        var subtotal = lineItems.fold(Money.zero(currency)) { acc, item ->
            (acc + item.amount).getOrNull() ?: acc
        }

        // Apply discount
        var discountAmount = Money.zero(currency)
        var updatedDiscount: Discount? = discount
        if (discount != null && !discount.isExpired()) {
            discountAmount = discount.apply(subtotal)
            updatedDiscount = discount.decrementCycle()
        }

        var total = (subtotal - discountAmount).mapLeft { ProcessRenewalError.Domain(it) }.bind()
        var finalTotal = if (total.isNegative()) Money.zero(currency) else total

        // Apply account credit balance
        var accountCreditApplied = Money.zero(currency)
        var updatedAccountBalance = subscription.accountCreditBalance

        if (subscription.accountCreditBalance.isPositive() && finalTotal.isPositive()) {
            val creditToApply = if (subscription.accountCreditBalance.amount >= finalTotal.amount) {
                finalTotal
            } else {
                subscription.accountCreditBalance
            }

            accountCreditApplied = creditToApply

            lineItems.add(
                InvoiceLineItem(
                    description = "Account credit applied",
                    amount = creditToApply.negate(),
                    type = InvoiceLineItemType.ACCOUNT_CREDIT,
                ),
            )

            finalTotal = (finalTotal - creditToApply).mapLeft { ProcessRenewalError.Domain(it) }.bind()
            updatedAccountBalance = (subscription.accountCreditBalance - creditToApply)
                .mapLeft { ProcessRenewalError.Domain(it) }.bind()

            // Recalculate subtotal to include account credit line
            subtotal = lineItems.filter { it.amount.isPositive() }.fold(Money.zero(currency)) { acc, item ->
                (acc + item.amount).getOrNull() ?: acc
            }
        }

        val invoice = Invoice(
            id = null,
            subscriptionId = subscription.id!!,
            lineItems = lineItems.toList(),
            subtotal = subtotal,
            discountAmount = discountAmount,
            total = finalTotal,
            currency = currency,
            status = InvoiceStatus.Draft,
            dueDate = subscription.currentPeriodEnd.atOffset(ZoneOffset.UTC).toLocalDate(),
            paidAt = null,
            paymentAttemptCount = 0,
            createdAt = now,
            updatedAt = now,
        )

        // Auto-mark as paid if zero total
        if (finalTotal.isZero()) {
            val paidInvoice = invoice.copy(
                status = InvoiceStatus.Paid(now),
                paidAt = now,
            )
            val newPeriodEnd = subscription.plan.billingInterval.nextPeriodEnd(subscription.currentPeriodEnd)

            transactionPort.run {
                invoiceRepository.save(paidInvoice)
                    .mapLeft { ProcessRenewalError.Domain(it) }
            }.bind()

            if (updatedDiscount != null && updatedDiscount != discount) {
                transactionPort.run {
                    discountRepository.save(updatedDiscount)
                        .mapLeft { ProcessRenewalError.Domain(it) }
                }.bind()
            }

            return@either transactionPort.run {
                subscriptionRepository.save(
                    subscription.copy(
                        currentPeriodStart = subscription.currentPeriodEnd,
                        currentPeriodEnd = newPeriodEnd,
                        pauseCountInPeriod = 0,
                        updatedAt = now,
                        accountCreditBalance = updatedAccountBalance,
                    ),
                ).mapLeft { ProcessRenewalError.Domain(it) }
            }.bind()
        }

        // Attempt payment
        val paymentResult = paymentGatewayPort.charge(
            finalTotal,
            subscription.paymentMethod!!,
            subscription.customerId.value.toString(),
        )

        if (paymentResult.isRight()) {
            val paidInvoice = invoice.copy(
                status = InvoiceStatus.Paid(now),
                paidAt = now,
                paymentAttemptCount = 1,
            )
            val newPeriodEnd = subscription.plan.billingInterval.nextPeriodEnd(subscription.currentPeriodEnd)

            transactionPort.run {
                invoiceRepository.save(paidInvoice)
                    .mapLeft { ProcessRenewalError.Domain(it) }
            }.bind()

            if (updatedDiscount != null && updatedDiscount != discount) {
                transactionPort.run {
                    discountRepository.save(updatedDiscount)
                        .mapLeft { ProcessRenewalError.Domain(it) }
                }.bind()
            }

            transactionPort.run {
                subscriptionRepository.save(
                    subscription.copy(
                        currentPeriodStart = subscription.currentPeriodEnd,
                        currentPeriodEnd = newPeriodEnd,
                        pauseCountInPeriod = 0,
                        updatedAt = now,
                        accountCreditBalance = updatedAccountBalance,
                    ),
                ).mapLeft { ProcessRenewalError.Domain(it) }
            }.bind()
        } else {
            // Payment failed -> PastDue
            val openInvoice = invoice.copy(
                status = InvoiceStatus.Open,
                paymentAttemptCount = 1,
            )
            val gracePeriodEnd = now.plus(Duration.ofDays(7))

            transactionPort.run {
                invoiceRepository.save(openInvoice)
                    .mapLeft { ProcessRenewalError.Domain(it) }
            }.bind()

            if (updatedDiscount != null && updatedDiscount != discount) {
                transactionPort.run {
                    discountRepository.save(updatedDiscount)
                        .mapLeft { ProcessRenewalError.Domain(it) }
                }.bind()
            }

            transactionPort.run {
                subscriptionRepository.save(
                    subscription.copy(
                        status = SubscriptionStatus.PastDue(gracePeriodEnd),
                        gracePeriodEnd = gracePeriodEnd,
                        updatedAt = now,
                        accountCreditBalance = updatedAccountBalance,
                    ),
                ).mapLeft { ProcessRenewalError.Domain(it) }
            }.bind()
        }
    }
}
