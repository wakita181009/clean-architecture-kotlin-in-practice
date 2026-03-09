package com.wakita181009.clean.domain.error

sealed interface DiscountError : DomainError {
    data class InvalidPercentageValue(val value: Int) : DiscountError {
        override val message: String = "Percentage value must be between 1 and 100, got $value"
    }

    data object FixedAmountMustBePositive : DiscountError {
        override val message: String = "Fixed amount discount value must be positive"
    }

    data class InvalidDurationMonths(val value: Int) : DiscountError {
        override val message: String = "Duration months must be between 1 and 24, got $value"
    }
}
