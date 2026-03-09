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
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "invoice_line_items")
class InvoiceLineItem(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    val invoice: Invoice,
    @Column(nullable = false)
    val description: String,
    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "amount", column = Column(name = "amount")),
        AttributeOverride(name = "currency", column = Column(name = "currency")),
    )
    val amount: Money,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: LineItemType,
)
