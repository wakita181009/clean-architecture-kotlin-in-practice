package com.wakita181009.classic.model

import jakarta.persistence.Column
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
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "discounts")
class Discount(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id")
    var subscription: Subscription? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: DiscountType,
    @Column(nullable = false, precision = 19, scale = 4)
    val value: BigDecimal,
    @Column(name = "duration_months")
    val durationMonths: Int? = null,
    @Column(name = "remaining_cycles")
    var remainingCycles: Int? = null,
    @Column(name = "applied_at", nullable = false)
    val appliedAt: Instant,
) {
    init {
        when (type) {
            DiscountType.PERCENTAGE -> {
                require(value >= BigDecimal.ONE) { "Percentage discount must be at least 1" }
                require(value <= BigDecimal("100")) { "Percentage discount must be at most 100" }
            }
            DiscountType.FIXED_AMOUNT -> {
                require(value > BigDecimal.ZERO) { "Fixed amount discount must be positive" }
            }
        }
        durationMonths?.let {
            require(it in 1..24) { "Duration months must be between 1 and 24" }
        }
    }
}
