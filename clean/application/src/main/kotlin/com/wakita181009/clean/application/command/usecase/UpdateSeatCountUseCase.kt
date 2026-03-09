package com.wakita181009.clean.application.command.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.wakita181009.clean.application.command.error.UpdateSeatCountError
import com.wakita181009.clean.application.command.port.AddOnQueryPort
import com.wakita181009.clean.application.command.port.PaymentGatewayPort
import com.wakita181009.clean.application.command.port.SubscriptionAddOnCommandQueryPort
import com.wakita181009.clean.application.command.port.SubscriptionCommandQueryPort
import com.wakita181009.clean.application.port.ClockPort
import com.wakita181009.clean.application.port.TransactionPort
import com.wakita181009.clean.domain.model.AddOnBillingType
import com.wakita181009.clean.domain.model.Invoice
import com.wakita181009.clean.domain.model.InvoiceLineItem
import com.wakita181009.clean.domain.model.InvoiceLineItemType
import com.wakita181009.clean.domain.model.InvoiceStatus
import com.wakita181009.clean.domain.model.Money
import com.wakita181009.clean.domain.model.Subscription
import com.wakita181009.clean.domain.model.SubscriptionId
import com.wakita181009.clean.domain.model.SubscriptionStatus
import com.wakita181009.clean.domain.repository.InvoiceRepository
import com.wakita181009.clean.domain.repository.SubscriptionAddOnRepository
import com.wakita181009.clean.domain.repository.SubscriptionRepository
import java.time.Duration
import java.time.ZoneOffset
import kotlin.math.abs

interface UpdateSeatCountUseCase {
    fun execute(subscriptionId: Long, newSeatCount: Int): Either<UpdateSeatCountError, Subscription>
}

class UpdateSeatCountUseCaseImpl(
    private val subscriptionCommandQueryPort: SubscriptionCommandQueryPort,
    private val subscriptionAddOnCommandQueryPort: SubscriptionAddOnCommandQueryPort,
    private val addOnQueryPort: AddOnQueryPort,
    private val subscriptionAddOnRepository: SubscriptionAddOnRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val invoiceRepository: InvoiceRepository,
    private val paymentGatewayPort: PaymentGatewayPort,
    private val clockPort: ClockPort,
    private val transactionPort: TransactionPort,
) : UpdateSeatCountUseCase {

    override fun execute(subscriptionId: Long, newSeatCount: Int): Either<UpdateSeatCountError, Subscription> = either {
        val subId = SubscriptionId(subscriptionId)
            .mapLeft { UpdateSeatCountError.InvalidInput("subscriptionId", it.message) }
            .bind()

        ensure(newSeatCount > 0) { UpdateSeatCountError.InvalidInput("seatCount", "Seat count must be positive") }

        val subscription = subscriptionCommandQueryPort.findById(subId)
            .mapLeft { UpdateSeatCountError.SubscriptionNotFound }
            .bind()

        ensure(subscription.status is SubscriptionStatus.Active) { UpdateSeatCountError.NotActive }
        ensure(subscription.plan.perSeatPricing) { UpdateSeatCountError.NotPerSeatPlan }

        val currentSeatCount = ensureNotNull(subscription.seatCount) { UpdateSeatCountError.NotPerSeatPlan }
        ensure(newSeatCount != currentSeatCount) { UpdateSeatCountError.SameSeatCount }
        ensure(newSeatCount >= subscription.plan.minSeats) { UpdateSeatCountError.BelowMinimum }
        if (subscription.plan.maxSeats != null) {
            ensure(newSeatCount <= subscription.plan.maxSeats!!) { UpdateSeatCountError.AboveMaximum }
        }

        val now = clockPort.now()
        val totalDays = Duration.between(subscription.currentPeriodStart, subscription.currentPeriodEnd).toDays()
        val daysRemaining = Duration.between(now, subscription.currentPeriodEnd).toDays().coerceAtLeast(0)

        val seatDifference = newSeatCount - currentSeatCount
        val absDifference = abs(seatDifference)
        val currency = subscription.plan.basePrice.currency

        val seatProrationAmount = subscription.plan.basePrice.multiply(
            absDifference.toLong() * daysRemaining,
            totalDays,
        )

        val lineItems = mutableListOf<InvoiceLineItem>()

        // Seat proration line item
        if (seatDifference > 0) {
            lineItems.add(
                InvoiceLineItem(
                    description = "Seat increase proration: $currentSeatCount -> $newSeatCount",
                    amount = seatProrationAmount,
                    type = InvoiceLineItemType.SEAT_PRORATION_CHARGE,
                ),
            )
        } else {
            lineItems.add(
                InvoiceLineItem(
                    description = "Seat decrease credit: $currentSeatCount -> $newSeatCount",
                    amount = seatProrationAmount.negate(),
                    type = InvoiceLineItemType.SEAT_PRORATION_CREDIT,
                ),
            )
        }

        // Handle PER_SEAT add-on proration
        val activeAddOns = subscriptionAddOnCommandQueryPort.findActiveBySubscriptionId(subId)
            .mapLeft { UpdateSeatCountError.Domain(it) }
            .bind()

        var totalAddonProration = Money.zero(currency)

        for (subscriptionAddOn in activeAddOns) {
            val addOn = addOnQueryPort.findActiveById(subscriptionAddOn.addOnId)
                .mapLeft { UpdateSeatCountError.Domain(it) }
                .bind()

            if (addOn != null && addOn.billingType == AddOnBillingType.PER_SEAT) {
                val addonProration = addOn.price.multiply(
                    absDifference.toLong() * daysRemaining,
                    totalDays,
                )

                if (seatDifference > 0) {
                    lineItems.add(
                        InvoiceLineItem(
                            description = "Add-on seat proration charge: ${addOn.name}",
                            amount = addonProration,
                            type = InvoiceLineItemType.ADDON_PRORATION_CHARGE,
                        ),
                    )
                    totalAddonProration = (totalAddonProration + addonProration)
                        .mapLeft { UpdateSeatCountError.Domain(it) }
                        .bind()
                } else {
                    lineItems.add(
                        InvoiceLineItem(
                            description = "Add-on seat proration credit: ${addOn.name}",
                            amount = addonProration.negate(),
                            type = InvoiceLineItemType.ADDON_PRORATION_CREDIT,
                        ),
                    )
                    totalAddonProration = (totalAddonProration + addonProration)
                        .mapLeft { UpdateSeatCountError.Domain(it) }
                        .bind()
                }
            }
        }

        val totalCharge = if (seatDifference > 0) {
            (seatProrationAmount + totalAddonProration)
                .mapLeft { UpdateSeatCountError.Domain(it) }
                .bind()
        } else {
            Money.zero(currency)
        }

        val totalCredit = if (seatDifference < 0) {
            (seatProrationAmount + totalAddonProration)
                .mapLeft { UpdateSeatCountError.Domain(it) }
                .bind()
        } else {
            Money.zero(currency)
        }

        val subtotal = lineItems.fold(Money.zero(currency)) { acc, item ->
            (acc + item.amount).getOrNull() ?: acc
        }

        val invoice = Invoice(
            id = null,
            subscriptionId = subscription.id!!,
            lineItems = lineItems.toList(),
            subtotal = subtotal,
            discountAmount = Money.zero(currency),
            total = subtotal,
            currency = currency,
            status = InvoiceStatus.Draft,
            dueDate = now.atOffset(ZoneOffset.UTC).toLocalDate(),
            paidAt = null,
            paymentAttemptCount = 0,
            createdAt = now,
            updatedAt = now,
        )

        if (seatDifference > 0) {
            // Charge immediately
            paymentGatewayPort.charge(
                totalCharge,
                subscription.paymentMethod!!,
                subscription.customerId.value.toString(),
            ).mapLeft { UpdateSeatCountError.PaymentFailed(it.reason) }
                .bind()

            transactionPort.run {
                invoiceRepository.save(
                    invoice.copy(
                        status = InvoiceStatus.Open.pay(now).getOrNull()!!,
                        paidAt = now,
                        paymentAttemptCount = 1,
                    ),
                ).mapLeft { UpdateSeatCountError.Domain(it) }
            }.bind()
        } else {
            // Credit to account balance
            transactionPort.run {
                invoiceRepository.save(invoice.copy(status = InvoiceStatus.Open))
                    .mapLeft { UpdateSeatCountError.Domain(it) }
            }.bind()

            val newBalance = (subscription.accountCreditBalance + totalCredit)
                .mapLeft { UpdateSeatCountError.Domain(it) }
                .bind()

            transactionPort.run {
                subscriptionRepository.save(
                    subscription.copy(
                        accountCreditBalance = newBalance,
                        updatedAt = now,
                    ),
                ).mapLeft { UpdateSeatCountError.Domain(it) }
            }.bind()
        }

        // Update PER_SEAT add-on quantities
        for (subscriptionAddOn in activeAddOns) {
            val addOn = addOnQueryPort.findActiveById(subscriptionAddOn.addOnId)
                .mapLeft { UpdateSeatCountError.Domain(it) }
                .bind()

            if (addOn != null && addOn.billingType == AddOnBillingType.PER_SEAT) {
                transactionPort.run {
                    subscriptionAddOnRepository.save(
                        subscriptionAddOn.copy(quantity = newSeatCount),
                    ).mapLeft { UpdateSeatCountError.Domain(it) }
                }.bind()
            }
        }

        // Update subscription seat count
        val updatedSubscription = subscription.copy(
            seatCount = newSeatCount,
            updatedAt = now,
            accountCreditBalance = if (seatDifference < 0) {
                (subscription.accountCreditBalance + totalCredit)
                    .mapLeft { UpdateSeatCountError.Domain(it) }
                    .bind()
            } else {
                subscription.accountCreditBalance
            },
        )

        transactionPort.run {
            subscriptionRepository.save(updatedSubscription)
                .mapLeft { UpdateSeatCountError.Domain(it) }
        }.bind()
    }
}
