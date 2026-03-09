package com.wakita181009.classic.model

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import java.math.BigDecimal
import java.math.RoundingMode

@Embeddable
data class Money(
    @Column(nullable = false)
    val amount: BigDecimal,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val currency: Currency,
) {
    enum class Currency(
        val scale: Int,
    ) {
        USD(2),
        EUR(2),
        JPY(0),
    }

    init {
        require(amount.scale() <= currency.scale) {
            "${currency.name} cannot have decimal places (scale ${amount.scale()} > ${currency.scale})"
        }
    }

    operator fun plus(other: Money): Money {
        require(currency == other.currency) { "Currency mismatch: $currency vs ${other.currency}" }
        return Money(amount.add(other.amount), currency)
    }

    operator fun minus(other: Money): Money {
        require(currency == other.currency) { "Currency mismatch: $currency vs ${other.currency}" }
        return Money(amount.subtract(other.amount), currency)
    }

    operator fun times(quantity: Int): Money =
        Money(amount.multiply(BigDecimal(quantity)).setScale(currency.scale, RoundingMode.HALF_UP), currency)

    fun times(
        numerator: Long,
        denominator: Long,
    ): Money {
        val result =
            amount
                .multiply(BigDecimal(numerator))
                .divide(BigDecimal(denominator), currency.scale, RoundingMode.HALF_UP)
        return Money(result, currency)
    }

    fun negate(): Money = Money(amount.negate(), currency)

    companion object {
        fun zero(currency: Currency): Money = Money(BigDecimal.ZERO.setScale(currency.scale), currency)
    }
}
