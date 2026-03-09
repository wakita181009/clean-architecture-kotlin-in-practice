package com.wakita181009.clean.domain.error

sealed interface SubscriptionAddOnError : DomainError {
    data object AlreadyDetached : SubscriptionAddOnError {
        override val message: String = "Subscription add-on is already detached (terminal state)"
    }
}
