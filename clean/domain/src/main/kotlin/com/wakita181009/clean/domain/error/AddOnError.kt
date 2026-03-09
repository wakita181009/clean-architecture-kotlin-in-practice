package com.wakita181009.clean.domain.error

sealed interface AddOnError : DomainError {
    data object BlankName : AddOnError {
        override val message: String = "Add-on name must not be blank"
    }

    data object PriceMustBePositive : AddOnError {
        override val message: String = "Add-on price must be greater than zero"
    }

    data object EmptyCompatibleTiers : AddOnError {
        override val message: String = "Compatible tiers must not be empty"
    }
}
