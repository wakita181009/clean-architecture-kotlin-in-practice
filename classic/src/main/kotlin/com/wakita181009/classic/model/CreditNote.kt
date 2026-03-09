package com.wakita181009.classic.model

import jakarta.persistence.AttributeOverride
import jakarta.persistence.AttributeOverrides
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(
    name = "credit_notes",
    indexes = [
        Index(name = "idx_credit_notes_invoice_id", columnList = "invoice_id"),
    ],
)
class CreditNote(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    val invoice: Invoice,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false)
    val subscription: Subscription,
    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "amount", column = Column(name = "amount")),
        AttributeOverride(name = "currency", column = Column(name = "currency")),
    )
    val amount: Money,
    @Column(nullable = false)
    val reason: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: CreditNoteType,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val application: CreditApplication,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: CreditNoteStatus = CreditNoteStatus.ISSUED,
    @Column(name = "refund_transaction_id")
    var refundTransactionId: String? = null,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
) {
    init {
        require(amount.amount > BigDecimal.ZERO) { "Credit note amount must be greater than zero" }
        require(reason.isNotBlank()) { "Credit note reason must not be blank" }
    }

    fun transitionTo(newStatus: CreditNoteStatus) {
        check(status.canTransitionTo(newStatus)) {
            "Cannot transition CreditNote from $status to $newStatus"
        }
        status = newStatus
    }
}
