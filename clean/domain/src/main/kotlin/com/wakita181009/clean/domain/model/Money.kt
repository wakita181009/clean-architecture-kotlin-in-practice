package com.wakita181009.clean.domain.model

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.wakita181009.clean.domain.error.MoneyError
import java.math.BigDecimal
import java.math.RoundingMode

data class Money(
    val amount: BigDecimal,
    val currency: Currency,
) {
    companion object {
        fun of(amount: BigDecimal, currency: Currency): Either<MoneyError, Money> {
            val scaled = amount.setScale(currency.scale, RoundingMode.HALF_UP)
            if (currency == Currency.JPY && amount.stripTrailingZeros().scale() > 0) {
                return MoneyError.InvalidJpyScale.left()
            }
            return Money(scaled, currency).right()
        }

        fun zero(currency: Currency): Money =
            Money(BigDecimal.ZERO.setScale(currency.scale, RoundingMode.HALF_UP), currency)
    }

    operator fun plus(other: Money): Either<MoneyError, Money> =
        if (currency != other.currency) {
            MoneyError.CurrencyMismatch(currency, other.currency).left()
        } else {
            Money(amount.add(other.amount).setScale(currency.scale, RoundingMode.HALF_UP), currency).right()
        }

    operator fun minus(other: Money): Either<MoneyError, Money> =
        if (currency != other.currency) {
            MoneyError.CurrencyMismatch(currency, other.currency).left()
        } else {
            Money(amount.subtract(other.amount).setScale(currency.scale, RoundingMode.HALF_UP), currency).right()
        }

    fun multiply(numerator: Long, denominator: Long): Money =
        Money(
            amount.multiply(BigDecimal(numerator))
                .divide(BigDecimal(denominator), currency.scale, RoundingMode.HALF_UP),
            currency,
        )

    fun negate(): Money = Money(amount.negate(), currency)

    fun isZero(): Boolean = amount.compareTo(BigDecimal.ZERO) == 0

    fun isPositive(): Boolean = amount > BigDecimal.ZERO

    fun isNegative(): Boolean = amount < BigDecimal.ZERO
}
