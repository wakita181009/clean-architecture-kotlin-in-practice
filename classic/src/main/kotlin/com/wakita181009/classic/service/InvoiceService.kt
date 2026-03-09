package com.wakita181009.classic.service

import com.wakita181009.classic.exception.BusinessRuleViolationException
import com.wakita181009.classic.exception.InvalidStateTransitionException
import com.wakita181009.classic.exception.InvoiceNotFoundException
import com.wakita181009.classic.exception.PaymentFailedException
import com.wakita181009.classic.model.Invoice
import com.wakita181009.classic.model.InvoiceStatus
import com.wakita181009.classic.model.SubscriptionStatus
import com.wakita181009.classic.repository.InvoiceRepository
import com.wakita181009.classic.repository.SubscriptionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

@Service
class InvoiceService(
    private val invoiceRepository: InvoiceRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val paymentGateway: PaymentGateway,
    private val clock: Clock,
) {
    companion object {
        private const val MAX_PAYMENT_ATTEMPTS = 3
    }

    @Transactional
    fun recoverPayment(invoiceId: Long): Invoice {
        val now = Instant.now(clock)
        val invoice =
            invoiceRepository
                .findById(invoiceId)
                .orElseThrow { InvoiceNotFoundException(invoiceId) }

        if (invoice.status != InvoiceStatus.OPEN) {
            throw InvalidStateTransitionException(invoice.status.name, "payment recovery (requires OPEN)")
        }

        val subscription = invoice.subscription
        if (subscription.status != SubscriptionStatus.PAST_DUE) {
            throw InvalidStateTransitionException(subscription.status.name, "payment recovery (requires PAST_DUE)")
        }

        // Check grace period
        val gracePeriodEnd = subscription.gracePeriodEnd
        if (gracePeriodEnd != null && now.isAfter(gracePeriodEnd)) {
            // Grace period expired
            subscription.transitionTo(SubscriptionStatus.CANCELED)
            subscription.updatedAt = now
            subscriptionRepository.save(subscription)
            invoice.transitionTo(InvoiceStatus.UNCOLLECTIBLE)
            invoice.updatedAt = now
            invoiceRepository.save(invoice)
            throw BusinessRuleViolationException("Grace period expired")
        }

        // Attempt payment
        val result = paymentGateway.charge(invoice.total, "CREDIT_CARD", subscription.customerId)
        invoice.paymentAttemptCount++
        invoice.updatedAt = now

        if (result.success) {
            invoice.transitionTo(InvoiceStatus.PAID)
            invoice.paidAt = now
            invoiceRepository.save(invoice)

            subscription.transitionTo(SubscriptionStatus.ACTIVE)
            subscription.gracePeriodEnd = null
            subscription.updatedAt = now
            subscriptionRepository.save(subscription)

            return invoice
        }

        // Payment failed
        if (invoice.paymentAttemptCount >= MAX_PAYMENT_ATTEMPTS) {
            invoice.transitionTo(InvoiceStatus.UNCOLLECTIBLE)
            invoiceRepository.save(invoice)

            subscription.transitionTo(SubscriptionStatus.CANCELED)
            subscription.updatedAt = now
            subscriptionRepository.save(subscription)

            return invoice
        }

        invoiceRepository.save(invoice)
        throw PaymentFailedException("Payment failed: ${result.errorReason}")
    }

    @Transactional(readOnly = true)
    fun listBySubscriptionId(subscriptionId: Long): List<Invoice> = invoiceRepository.findBySubscriptionId(subscriptionId)
}
