package com.wakita181009.clean.application.command.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.wakita181009.clean.application.command.error.RecoverPaymentError
import com.wakita181009.clean.application.command.port.InvoiceCommandQueryPort
import com.wakita181009.clean.application.command.port.PaymentGatewayPort
import com.wakita181009.clean.application.command.port.SubscriptionCommandQueryPort
import com.wakita181009.clean.application.port.ClockPort
import com.wakita181009.clean.application.port.TransactionPort
import com.wakita181009.clean.domain.model.Invoice
import com.wakita181009.clean.domain.model.InvoiceId
import com.wakita181009.clean.domain.model.InvoiceStatus
import com.wakita181009.clean.domain.model.SubscriptionStatus
import com.wakita181009.clean.domain.repository.InvoiceRepository
import com.wakita181009.clean.domain.repository.SubscriptionRepository

interface RecoverPaymentUseCase {
    fun execute(invoiceId: Long): Either<RecoverPaymentError, Invoice>
}

class RecoverPaymentUseCaseImpl(
    private val invoiceCommandQueryPort: InvoiceCommandQueryPort,
    private val subscriptionCommandQueryPort: SubscriptionCommandQueryPort,
    private val paymentGatewayPort: PaymentGatewayPort,
    private val invoiceRepository: InvoiceRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val clockPort: ClockPort,
    private val transactionPort: TransactionPort,
) : RecoverPaymentUseCase {

    override fun execute(invoiceId: Long): Either<RecoverPaymentError, Invoice> = either {
        val invId = InvoiceId(invoiceId)
            .mapLeft { RecoverPaymentError.InvalidInput("invoiceId", it.message) }
            .bind()

        val invoice = invoiceCommandQueryPort.findById(invId)
            .mapLeft { RecoverPaymentError.InvoiceNotFound }
            .bind()

        ensure(invoice.status is InvoiceStatus.Open) { RecoverPaymentError.InvoiceNotOpen }

        val subscription = subscriptionCommandQueryPort.findById(invoice.subscriptionId)
            .mapLeft { RecoverPaymentError.Internal(it.message) }
            .bind()

        val pastDueStatus = subscription.status
        ensure(pastDueStatus is SubscriptionStatus.PastDue) { RecoverPaymentError.SubscriptionNotPastDue }

        val now = clockPort.now()

        // Check grace period
        if (pastDueStatus.gracePeriodEnd.isBefore(now)) {
            // Grace period expired -> cancel subscription and mark uncollectible
            transactionPort.run {
                subscriptionRepository.save(
                    subscription.copy(
                        status = SubscriptionStatus.Canceled(now),
                        canceledAt = now,
                        updatedAt = now,
                    ),
                ).mapLeft { RecoverPaymentError.Domain(it) }
            }.bind()

            transactionPort.run {
                invoiceRepository.save(
                    invoice.copy(
                        status = InvoiceStatus.Uncollectible,
                        updatedAt = now,
                    ),
                ).mapLeft { RecoverPaymentError.Domain(it) }
            }.bind()

            raise(RecoverPaymentError.GracePeriodExpired)
        }

        // Attempt payment
        val paymentResult = paymentGatewayPort.charge(
            invoice.total,
            subscription.paymentMethod!!,
            subscription.customerId.value.toString(),
        )

        if (paymentResult.isRight()) {
            // Payment succeeded
            val paidInvoice = transactionPort.run {
                invoiceRepository.save(
                    invoice.copy(
                        status = InvoiceStatus.Paid(now),
                        paidAt = now,
                        paymentAttemptCount = invoice.paymentAttemptCount + 1,
                        updatedAt = now,
                    ),
                ).mapLeft { RecoverPaymentError.Domain(it) }
            }.bind()

            transactionPort.run {
                subscriptionRepository.save(
                    subscription.copy(
                        status = SubscriptionStatus.Active,
                        gracePeriodEnd = null,
                        updatedAt = now,
                    ),
                ).mapLeft { RecoverPaymentError.Domain(it) }
            }.bind()

            paidInvoice
        } else {
            // Payment failed
            val newAttemptCount = invoice.paymentAttemptCount + 1

            if (newAttemptCount >= 3) {
                // Max attempts reached -> uncollectible + cancel
                val uncollectibleInvoice = transactionPort.run {
                    invoiceRepository.save(
                        invoice.copy(
                            status = InvoiceStatus.Uncollectible,
                            paymentAttemptCount = newAttemptCount,
                            updatedAt = now,
                        ),
                    ).mapLeft { RecoverPaymentError.Domain(it) }
                }.bind()

                transactionPort.run {
                    subscriptionRepository.save(
                        subscription.copy(
                            status = SubscriptionStatus.Canceled(now),
                            canceledAt = now,
                            updatedAt = now,
                        ),
                    ).mapLeft { RecoverPaymentError.Domain(it) }
                }.bind()

                uncollectibleInvoice
            } else {
                // Increment attempt count, keep open
                val updatedInvoice = transactionPort.run {
                    invoiceRepository.save(
                        invoice.copy(
                            paymentAttemptCount = newAttemptCount,
                            updatedAt = now,
                        ),
                    ).mapLeft { RecoverPaymentError.Domain(it) }
                }.bind()

                val reason = paymentResult.leftOrNull()?.reason ?: "Unknown"
                raise(RecoverPaymentError.PaymentFailed(reason))
            }
        }
    }
}
