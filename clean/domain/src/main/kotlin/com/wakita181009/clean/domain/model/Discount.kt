package com.wakita181009.clean.domain.model

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.wakita181009.clean.domain.error.DiscountError
import java.math.BigDecimal
import java.time.Instant

data class Discount(
    val id: DiscountId?,
    val subscriptionId: SubscriptionId,
    val type: DiscountType,
    val percentageValue: Int?,
    val fixedAmountMoney: Money?,
    val durationMonths: Int?,
    val remainingCycles: Int?,
    val appliedAt: Instant,
) {
    companion object {
        fun of(
            id: DiscountId?,
            subscriptionId: SubscriptionId,
            type: DiscountType,
            percentageValue: Int?,
            fixedAmountMoney: Money?,
            durationMonths: Int?,
            remainingCycles: Int?,
            appliedAt: Instant,
        ): Either<DiscountError, Discount> = either {
            when (type) {
                DiscountType.PERCENTAGE -> {
                    val pv = percentageValue ?: 0
                    ensure(pv in 1..100) { DiscountError.InvalidPercentageValue(pv) }
                }
                DiscountType.FIXED_AMOUNT -> {
                    val money = fixedAmountMoney
                    ensure(money != null && money.amount > BigDecimal.ZERO) { DiscountError.FixedAmountMustBePositive }
                }
            }
            if (durationMonths != null) {
                ensure(durationMonths in 1..24) { DiscountError.InvalidDurationMonths(durationMonths) }
            }
            Discount(id, subscriptionId, type, percentageValue, fixedAmountMoney, durationMonths, remainingCycles, appliedAt)
        }
    }

    fun apply(subtotal: Money): Money =
        when (type) {
            DiscountType.PERCENTAGE -> {
                val pv = percentageValue ?: 0
                subtotal.multiply(pv.toLong(), 100L)
            }
            DiscountType.FIXED_AMOUNT -> {
                val fixed = fixedAmountMoney ?: Money.zero(subtotal.currency)
                if (fixed.amount > subtotal.amount) subtotal else fixed
            }
        }

    fun decrementCycle(): Discount =
        if (remainingCycles != null) {
            copy(remainingCycles = (remainingCycles - 1).coerceAtLeast(0))
        } else {
            this
        }

    fun isExpired(): Boolean = remainingCycles != null && remainingCycles <= 0
}
