package com.wakita181009.classic.model

import jakarta.persistence.AttributeOverride
import jakarta.persistence.AttributeOverrides
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "addons")
class AddOn(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(nullable = false)
    val name: String,
    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "amount", column = Column(name = "price_amount")),
        AttributeOverride(name = "currency", column = Column(name = "price_currency")),
    )
    val price: Money,
    @Enumerated(EnumType.STRING)
    @Column(name = "billing_type", nullable = false)
    val billingType: BillingType,
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "addon_compatible_tiers", joinColumns = [JoinColumn(name = "addon_id")])
    @Enumerated(EnumType.STRING)
    @Column(name = "tier")
    val compatibleTiers: Set<PlanTier> = emptySet(),
    @Column(nullable = false)
    val active: Boolean = true,
) {
    val currency: Money.Currency
        get() = price.currency

    init {
        require(name.isNotBlank()) { "Add-on name must not be blank" }
        require(price.amount > BigDecimal.ZERO) { "Add-on price must be greater than zero" }
        require(compatibleTiers.isNotEmpty()) { "Compatible tiers must not be empty" }
    }
}
