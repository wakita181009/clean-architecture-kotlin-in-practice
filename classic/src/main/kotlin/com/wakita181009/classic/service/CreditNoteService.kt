package com.wakita181009.classic.service

import com.wakita181009.classic.exception.AlreadyFullyRefundedException
import com.wakita181009.classic.exception.CreditAmountExceedsRemainingException
import com.wakita181009.classic.exception.InvoiceNotFoundException
import com.wakita181009.classic.exception.InvoiceNotPaidException
import com.wakita181009.classic.exception.PaymentFailedException
import com.wakita181009.classic.model.CreditApplication
import com.wakita181009.classic.model.CreditNote
import com.wakita181009.classic.model.CreditNoteStatus
import com.wakita181009.classic.model.CreditNoteType
import com.wakita181009.classic.model.InvoiceStatus
import com.wakita181009.classic.model.Money
import com.wakita181009.classic.repository.CreditNoteRepository
import com.wakita181009.classic.repository.InvoiceRepository
import com.wakita181009.classic.repository.SubscriptionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant

@Service
class CreditNoteService(
    private val invoiceRepository: InvoiceRepository,
    private val creditNoteRepository: CreditNoteRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val paymentGateway: PaymentGateway,
    private val clock: Clock,
) {
    @Transactional
    fun issueCreditNote(
        invoiceId: Long,
        type: CreditNoteType,
        application: CreditApplication,
        amount: BigDecimal?,
        reason: String,
    ): CreditNote {
        val now = Instant.now(clock)
        val invoice =
            invoiceRepository
                .findById(invoiceId)
                .orElseThrow { InvoiceNotFoundException(invoiceId) }

        if (invoice.status != InvoiceStatus.PAID) {
            throw InvoiceNotPaidException(invoiceId)
        }

        // Calculate remaining refundable amount
        val existingCredits =
            creditNoteRepository
                .findByInvoiceIdAndStatusIn(invoiceId, listOf(CreditNoteStatus.ISSUED, CreditNoteStatus.APPLIED))
        val totalCredited =
            existingCredits.fold(BigDecimal.ZERO) { acc, cn -> acc.add(cn.amount.amount) }
        val remaining = invoice.total.amount.subtract(totalCredited)

        val creditAmount: BigDecimal
        when (type) {
            CreditNoteType.FULL -> {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                    throw AlreadyFullyRefundedException(invoiceId)
                }
                creditAmount = remaining
            }
            CreditNoteType.PARTIAL -> {
                requireNotNull(amount) { "Amount is required for partial credit notes" }
                require(amount > BigDecimal.ZERO) { "Partial credit note amount must be greater than zero" }
                if (amount > remaining) {
                    throw CreditAmountExceedsRemainingException(remaining.toPlainString(), amount.toPlainString())
                }
                creditAmount = amount
            }
        }

        val currency = invoice.total.currency
        val creditMoney = Money(creditAmount.setScale(currency.scale, java.math.RoundingMode.HALF_UP), currency)

        val subscription = invoice.subscription

        val creditNote =
            CreditNote(
                invoice = invoice,
                subscription = subscription,
                amount = creditMoney,
                reason = reason,
                type = type,
                application = application,
                status = CreditNoteStatus.ISSUED,
                createdAt = now,
                updatedAt = now,
            )

        val savedCreditNote = creditNoteRepository.save(creditNote)

        when (application) {
            CreditApplication.REFUND_TO_PAYMENT -> {
                // Find the transaction ID from the invoice's paid payment
                // For simplicity, use invoice ID as transaction reference
                val transactionId = "invoice-${invoice.id}"
                val refundResult = paymentGateway.refund(transactionId, creditMoney)
                if (refundResult.success) {
                    savedCreditNote.refundTransactionId = refundResult.refundTransactionId
                    savedCreditNote.transitionTo(CreditNoteStatus.APPLIED)
                    savedCreditNote.updatedAt = now
                } else {
                    // Stays as ISSUED, caller gets 502
                    creditNoteRepository.save(savedCreditNote)
                    throw PaymentFailedException("Refund failed: ${refundResult.errorReason}")
                }
            }
            CreditApplication.ACCOUNT_CREDIT -> {
                // Add to subscription's account credit balance
                subscription.accountCreditBalance = subscription.accountCreditBalance + creditMoney
                subscription.updatedAt = now
                subscriptionRepository.save(subscription)

                savedCreditNote.transitionTo(CreditNoteStatus.APPLIED)
                savedCreditNote.updatedAt = now
            }
        }

        return creditNoteRepository.save(savedCreditNote)
    }

    @Transactional(readOnly = true)
    fun listCreditNotes(invoiceId: Long): List<CreditNote> = creditNoteRepository.findByInvoiceId(invoiceId)
}
