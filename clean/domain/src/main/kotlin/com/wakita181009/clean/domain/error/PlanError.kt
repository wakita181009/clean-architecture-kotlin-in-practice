package com.wakita181009.clean.domain.error

sealed interface PlanError : DomainError {
    data object FreeTierMustBeZeroPrice : PlanError {
        override val message: String = "FREE tier plans must have base price of zero"
    }

    data object NonFreeTierMustHavePositivePrice : PlanError {
        override val message: String = "Non-FREE tier plans must have positive base price"
    }

    data object EmptyFeatures : PlanError {
        override val message: String = "Features must not be empty"
    }

    data object BlankName : PlanError {
        override val message: String = "Plan name must not be blank"
    }
}
