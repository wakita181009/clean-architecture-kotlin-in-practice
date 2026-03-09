package com.wakita181009.clean.application.command.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.wakita181009.clean.application.command.error.IssueCreditNoteError
import com.wakita181009.clean.application.command.port.CreditNoteCommandQueryPort
import com.wakita181009.clean.application.command.port.InvoiceCommandQueryPort
import com.wakita181009.clean.application.command.port.PaymentGatewayPort
import com.wakita181009.clean.application.command.port.SubscriptionCommandQueryPort
import com.wakita181009.clean.application.port.ClockPort
import com.wakita181009.clean.application.port.TransactionPort
import com.wakita181009.clean.domain.model.CreditNote
import com.wakita181009.clean.domain.model.CreditNoteApplication
import com.wakita181009.clean.domain.model.CreditNoteStatus
import com.wakita181009.clean.domain.model.CreditNoteType
import com.wakita181009.clean.domain.model.InvoiceId
import com.wakita181009.clean.domain.model.InvoiceStatus
import com.wakita181009.clean.domain.model.Money
import com.wakita181009.clean.domain.repository.CreditNoteRepository
import com.wakita181009.clean.domain.repository.SubscriptionRepository
import java.math.BigDecimal

interface IssueCreditNoteUseCase {
    fun execute(
        invoiceId: Long,
        type: String,
        application: String,
        amount: BigDecimal?,
        reason: String,
    ): Either<IssueCreditNoteError, CreditNote>
}

class IssueCreditNoteUseCaseImpl(
    private val invoiceCommandQueryPort: InvoiceCommandQueryPort,
    private val subscriptionCommandQueryPort: SubscriptionCommandQueryPort,
    private val creditNoteCommandQueryPort: CreditNoteCommandQueryPort,
    private val creditNoteRepository: CreditNoteRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val paymentGatewayPort: PaymentGatewayPort,
    private val clockPort: ClockPort,
    private val transactionPort: TransactionPort,
) : IssueCreditNoteUseCase {

    override fun execute(
        invoiceId: Long,
        type: String,
        application: String,
        amount: BigDecimal?,
        reason: String,
    ): Either<IssueCreditNoteError, CreditNote> = either {
        val invId = InvoiceId(invoiceId)
            .mapLeft { IssueCreditNoteError.InvalidInput("invoiceId", it.message) }
            .bind()

        ensure(reason.isNotBlank()) { IssueCreditNoteError.InvalidInput("reason", "Reason must not be blank") }

        val creditNoteType = Either.catch { CreditNoteType.valueOf(type) }
            .mapLeft { IssueCreditNoteError.InvalidInput("type", "Invalid type: $type") }
            .bind()

        val creditNoteApplication = Either.catch { CreditNoteApplication.valueOf(application) }
            .mapLeft { IssueCreditNoteError.InvalidInput("application", "Invalid application: $application") }
            .bind()

        val invoice = invoiceCommandQueryPort.findById(invId)
            .mapLeft { IssueCreditNoteError.InvoiceNotFound }
            .bind()

        ensure(invoice.status is InvoiceStatus.Paid) { IssueCreditNoteError.InvoiceNotPaid }

        val existingCreditNotes = creditNoteCommandQueryPort.findByInvoiceId(invId)
            .mapLeft { IssueCreditNoteError.Domain(it) }
            .bind()

        val totalCredited = existingCreditNotes.fold(Money.zero(invoice.currency)) { acc, cn ->
            (acc + cn.amount).getOrNull() ?: acc
        }

        val remaining = (invoice.total - totalCredited)
            .mapLeft { IssueCreditNoteError.Domain(it) }
            .bind()

        val creditAmount = when (creditNoteType) {
            CreditNoteType.FULL -> {
                ensure(remaining.isPositive()) { IssueCreditNoteError.AlreadyFullyRefunded }
                remaining
            }
            CreditNoteType.PARTIAL -> {
                val partialAmount = Money.of(amount ?: BigDecimal.ZERO, invoice.currency)
                    .mapLeft { IssueCreditNoteError.Domain(it) }
                    .bind()
                ensure(partialAmount.isPositive()) {
                    IssueCreditNoteError.InvalidInput("amount", "Amount must be greater than zero")
                }
                ensure(partialAmount.amount <= remaining.amount) {
                    IssueCreditNoteError.AmountExceedsRemaining(remaining.amount.toPlainString())
                }
                partialAmount
            }
        }

        val now = clockPort.now()

        val creditNote = CreditNote.of(
            id = null,
            invoiceId = invId,
            subscriptionId = invoice.subscriptionId,
            amount = creditAmount,
            reason = reason,
            type = creditNoteType,
            application = creditNoteApplication,
            status = CreditNoteStatus.Issued,
            refundTransactionId = null,
            createdAt = now,
            updatedAt = now,
        ).mapLeft { IssueCreditNoteError.Domain(it) }.bind()

        when (creditNoteApplication) {
            CreditNoteApplication.REFUND_TO_PAYMENT -> {
                // Find original transaction ID from the invoice payment
                val paidAt = (invoice.status as InvoiceStatus.Paid).paidAt
                val refundResult = paymentGatewayPort.refund(
                    "txn_${invoice.id!!.value}",
                    creditAmount,
                ).mapLeft { IssueCreditNoteError.PaymentFailed(it.reason) }

                if (refundResult.isRight()) {
                    val txnId = refundResult.getOrNull()!!.refundTransactionId
                    val appliedNote = creditNote.copy(
                        status = CreditNoteStatus.Applied,
                        refundTransactionId = txnId,
                        updatedAt = now,
                    )
                    transactionPort.run {
                        creditNoteRepository.save(appliedNote)
                            .mapLeft { IssueCreditNoteError.Domain(it) }
                    }.bind()
                } else {
                    // Save as Issued (can retry later)
                    val savedNote = transactionPort.run {
                        creditNoteRepository.save(creditNote)
                            .mapLeft { IssueCreditNoteError.Domain(it) }
                    }.bind()
                    // Propagate the payment error
                    refundResult.mapLeft { it as IssueCreditNoteError }.bind()
                    savedNote // This won't be reached
                }
            }
            CreditNoteApplication.ACCOUNT_CREDIT -> {
                val appliedNote = creditNote.copy(
                    status = CreditNoteStatus.Applied,
                    updatedAt = now,
                )

                // Add to account credit balance
                val subscription = subscriptionCommandQueryPort.findById(invoice.subscriptionId)
                    .mapLeft { IssueCreditNoteError.Internal("Subscription not found") }
                    .bind()

                val newBalance = (subscription.accountCreditBalance + creditAmount)
                    .mapLeft { IssueCreditNoteError.Domain(it) }
                    .bind()

                transactionPort.run {
                    subscriptionRepository.save(
                        subscription.copy(
                            accountCreditBalance = newBalance,
                            updatedAt = now,
                        ),
                    ).mapLeft { IssueCreditNoteError.Domain(it) }
                }.bind()

                transactionPort.run {
                    creditNoteRepository.save(appliedNote)
                        .mapLeft { IssueCreditNoteError.Domain(it) }
                }.bind()
            }
        }
    }
}
