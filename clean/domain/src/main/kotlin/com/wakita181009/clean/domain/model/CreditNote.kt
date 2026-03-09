package com.wakita181009.clean.domain.model

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.wakita181009.clean.domain.error.CreditNoteError
import java.math.BigDecimal
import java.time.Instant

data class CreditNote(
    val id: CreditNoteId?,
    val invoiceId: InvoiceId,
    val subscriptionId: SubscriptionId,
    val amount: Money,
    val reason: String,
    val type: CreditNoteType,
    val application: CreditNoteApplication,
    val status: CreditNoteStatus,
    val refundTransactionId: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun of(
            id: CreditNoteId?,
            invoiceId: InvoiceId,
            subscriptionId: SubscriptionId,
            amount: Money,
            reason: String,
            type: CreditNoteType,
            application: CreditNoteApplication,
            status: CreditNoteStatus,
            refundTransactionId: String?,
            createdAt: Instant,
            updatedAt: Instant,
        ): Either<CreditNoteError, CreditNote> = either {
            ensure(amount.amount > BigDecimal.ZERO) { CreditNoteError.AmountMustBePositive }
            ensure(reason.isNotBlank()) { CreditNoteError.BlankReason }
            CreditNote(id, invoiceId, subscriptionId, amount, reason, type, application, status, refundTransactionId, createdAt, updatedAt)
        }
    }
}
