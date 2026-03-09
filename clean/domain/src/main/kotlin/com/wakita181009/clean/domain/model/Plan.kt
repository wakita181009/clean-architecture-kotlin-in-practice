package com.wakita181009.clean.domain.model

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.wakita181009.clean.domain.error.PlanError
import java.math.BigDecimal

data class Plan(
    val id: PlanId,
    val name: String,
    val billingInterval: BillingInterval,
    val basePrice: Money,
    val usageLimit: Int?,
    val features: Set<String>,
    val tier: PlanTier,
    val active: Boolean,
    val perSeatPricing: Boolean = false,
    val minSeats: Int = 1,
    val maxSeats: Int? = null,
) {
    companion object {
        fun of(
            id: PlanId,
            name: String,
            billingInterval: BillingInterval,
            basePrice: Money,
            usageLimit: Int?,
            features: Set<String>,
            tier: PlanTier,
            active: Boolean,
            perSeatPricing: Boolean = false,
            minSeats: Int = 1,
            maxSeats: Int? = null,
        ): Either<PlanError, Plan> = either {
            ensure(name.isNotBlank()) { PlanError.BlankName }
            ensure(features.isNotEmpty()) { PlanError.EmptyFeatures }
            if (tier == PlanTier.FREE) {
                ensure(basePrice.amount.compareTo(BigDecimal.ZERO) == 0) { PlanError.FreeTierMustBeZeroPrice }
                ensure(!perSeatPricing) { PlanError.FreeTierCannotBePerSeat }
            } else {
                ensure(basePrice.amount > BigDecimal.ZERO) { PlanError.NonFreeTierMustHavePositivePrice }
            }
            if (perSeatPricing) {
                ensure(minSeats >= 1) { PlanError.MinSeatsMustBeAtLeastOne }
                if (maxSeats != null) {
                    ensure(maxSeats >= minSeats) { PlanError.MinSeatsExceedsMaxSeats }
                }
            }
            Plan(id, name, billingInterval, basePrice, usageLimit, features, tier, active, perSeatPricing, minSeats, maxSeats)
        }
    }
}
