package com.wakita181009.clean.application.command.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.wakita181009.clean.application.command.dto.CancelSubscriptionCommand
import com.wakita181009.clean.application.command.error.CancelSubscriptionError
import com.wakita181009.clean.application.command.port.InvoiceCommandQueryPort
import com.wakita181009.clean.application.command.port.SubscriptionCommandQueryPort
import com.wakita181009.clean.application.port.ClockPort
import com.wakita181009.clean.application.port.TransactionPort
import com.wakita181009.clean.domain.model.InvoiceStatus
import com.wakita181009.clean.domain.model.Subscription
import com.wakita181009.clean.domain.model.SubscriptionId
import com.wakita181009.clean.domain.model.SubscriptionStatus
import com.wakita181009.clean.domain.repository.InvoiceRepository
import com.wakita181009.clean.domain.repository.SubscriptionRepository

interface CancelSubscriptionUseCase {
    fun execute(command: CancelSubscriptionCommand): Either<CancelSubscriptionError, Subscription>
}

class CancelSubscriptionUseCaseImpl(
    private val subscriptionCommandQueryPort: SubscriptionCommandQueryPort,
    private val invoiceCommandQueryPort: InvoiceCommandQueryPort,
    private val subscriptionRepository: SubscriptionRepository,
    private val invoiceRepository: InvoiceRepository,
    private val clockPort: ClockPort,
    private val transactionPort: TransactionPort,
) : CancelSubscriptionUseCase {

    override fun execute(command: CancelSubscriptionCommand): Either<CancelSubscriptionError, Subscription> = either {
        val subId = SubscriptionId(command.subscriptionId)
            .mapLeft { CancelSubscriptionError.InvalidInput("subscriptionId", it.message) }
            .bind()

        val subscription = subscriptionCommandQueryPort.findById(subId)
            .mapLeft { CancelSubscriptionError.SubscriptionNotFound }
            .bind()

        val now = clockPort.now()

        // Check terminal states
        ensure(subscription.status !is SubscriptionStatus.Canceled) { CancelSubscriptionError.AlreadyTerminal }
        ensure(subscription.status !is SubscriptionStatus.Expired) { CancelSubscriptionError.AlreadyTerminal }

        if (command.immediate) {
            // Immediate cancellation
            val canceledStatus = when (val status = subscription.status) {
                is SubscriptionStatus.Active -> status.cancel(now)
                is SubscriptionStatus.Paused -> status.cancel(now)
                is SubscriptionStatus.PastDue -> status.cancel(now)
                is SubscriptionStatus.Trial -> SubscriptionStatus.Canceled(now).let { Either.Right(it) }
                else -> Either.Left(com.wakita181009.clean.domain.error.SubscriptionError.InvalidTransition(
                    subscription.status.name, "CANCELED",
                ))
            }.mapLeft { CancelSubscriptionError.Domain(it) }.bind()

            // Void open invoices
            val openInvoices = invoiceCommandQueryPort.findOpenBySubscriptionId(subscription.id!!)
                .mapLeft { CancelSubscriptionError.Domain(it) }
                .bind()

            transactionPort.run {
                for (invoice in openInvoices) {
                    val voidedInvoice = when (val status = invoice.status) {
                        is InvoiceStatus.Open -> invoice.copy(
                            status = status.void().getOrNull()!!,
                            updatedAt = now,
                        )
                        else -> invoice
                    }
                    invoiceRepository.save(voidedInvoice)
                        .mapLeft { CancelSubscriptionError.Domain(it) }
                }

                subscriptionRepository.save(
                    subscription.copy(
                        status = canceledStatus,
                        canceledAt = now,
                        updatedAt = now,
                    ),
                ).mapLeft { CancelSubscriptionError.Domain(it) }
            }.bind()
        } else {
            // End-of-period cancellation
            ensure(subscription.status !is SubscriptionStatus.Paused) {
                CancelSubscriptionError.CannotEndOfPeriodForPaused
            }
            ensure(
                subscription.status is SubscriptionStatus.Active ||
                    subscription.status is SubscriptionStatus.PastDue,
            ) { CancelSubscriptionError.AlreadyTerminal }

            transactionPort.run {
                subscriptionRepository.save(
                    subscription.copy(
                        cancelAtPeriodEnd = true,
                        canceledAt = now,
                        updatedAt = now,
                    ),
                ).mapLeft { CancelSubscriptionError.Domain(it) }
            }.bind()
        }
    }
}
