package com.wakita181009.classic.model

import jakarta.persistence.AttributeOverride
import jakarta.persistence.AttributeOverrides
import jakarta.persistence.CascadeType
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
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(
    name = "invoices",
    indexes = [
        Index(name = "idx_invoices_subscription_id", columnList = "subscription_id"),
    ],
)
class Invoice(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false)
    val subscription: Subscription,
    @OneToMany(mappedBy = "invoice", cascade = [CascadeType.ALL], orphanRemoval = true)
    val lineItems: MutableList<InvoiceLineItem> = mutableListOf(),
    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "amount", column = Column(name = "subtotal_amount")),
        AttributeOverride(name = "currency", column = Column(name = "subtotal_currency")),
    )
    var subtotal: Money,
    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "amount", column = Column(name = "discount_amount")),
        AttributeOverride(name = "currency", column = Column(name = "discount_currency")),
    )
    var discountAmount: Money,
    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "amount", column = Column(name = "total_amount")),
        AttributeOverride(name = "currency", column = Column(name = "total_currency")),
    )
    var total: Money,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: InvoiceStatus = InvoiceStatus.DRAFT,
    @Column(name = "due_date", nullable = false)
    val dueDate: LocalDate,
    @Column(name = "paid_at")
    var paidAt: Instant? = null,
    @Column(name = "payment_attempt_count", nullable = false)
    var paymentAttemptCount: Int = 0,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
) {
    fun transitionTo(newStatus: InvoiceStatus) {
        check(status.canTransitionTo(newStatus)) {
            "Cannot transition invoice from $status to $newStatus"
        }
        status = newStatus
    }

    fun addLineItem(lineItem: InvoiceLineItem) {
        lineItems.add(lineItem)
    }
}
