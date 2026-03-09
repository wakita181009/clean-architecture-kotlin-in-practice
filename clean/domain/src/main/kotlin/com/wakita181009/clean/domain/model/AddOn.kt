package com.wakita181009.clean.domain.model

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.wakita181009.clean.domain.error.AddOnError
import java.math.BigDecimal

data class AddOn(
    val id: AddOnId,
    val name: String,
    val price: Money,
    val billingType: AddOnBillingType,
    val compatibleTiers: Set<PlanTier>,
    val active: Boolean,
) {
    companion object {
        fun of(
            id: AddOnId,
            name: String,
            price: Money,
            billingType: AddOnBillingType,
            compatibleTiers: Set<PlanTier>,
            active: Boolean,
        ): Either<AddOnError, AddOn> = either {
            ensure(name.isNotBlank()) { AddOnError.BlankName }
            ensure(price.amount > BigDecimal.ZERO) { AddOnError.PriceMustBePositive }
            ensure(compatibleTiers.isNotEmpty()) { AddOnError.EmptyCompatibleTiers }
            AddOn(id, name, price, billingType, compatibleTiers, active)
        }
    }
}
