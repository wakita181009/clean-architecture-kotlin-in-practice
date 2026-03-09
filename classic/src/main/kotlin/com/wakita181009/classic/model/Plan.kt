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
@Table(name = "plans")
class Plan(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(nullable = false)
    val name: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "billing_interval", nullable = false)
    val billingInterval: BillingInterval,
    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "amount", column = Column(name = "base_price_amount")),
        AttributeOverride(name = "currency", column = Column(name = "base_price_currency")),
    )
    val basePrice: Money,
    @Column(name = "usage_limit")
    val usageLimit: Int? = null,
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "plan_features", joinColumns = [JoinColumn(name = "plan_id")])
    @Column(name = "feature")
    val features: Set<String> = emptySet(),
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val tier: PlanTier,
    @Column(nullable = false)
    val active: Boolean = true,
    @Column(name = "per_seat_pricing", nullable = false)
    val perSeatPricing: Boolean = false,
    @Column(name = "minimum_seats", nullable = false)
    val minimumSeats: Int = 1,
    @Column(name = "maximum_seats")
    val maximumSeats: Int? = null,
) {
    init {
        if (tier == PlanTier.FREE) {
            require(basePrice.amount.compareTo(BigDecimal.ZERO) == 0) {
                "FREE tier plans must have zero base price"
            }
            require(!perSeatPricing) {
                "FREE tier cannot have per-seat pricing"
            }
        } else {
            require(basePrice.amount > BigDecimal.ZERO) {
                "Non-FREE tier plans must have positive base price"
            }
        }
        if (perSeatPricing) {
            require(minimumSeats >= 1) {
                "Minimum seats must be at least 1 for per-seat plans"
            }
            maximumSeats?.let { max ->
                require(max >= minimumSeats) {
                    "Maximum seats must be >= minimum seats"
                }
            }
        }
    }
}
