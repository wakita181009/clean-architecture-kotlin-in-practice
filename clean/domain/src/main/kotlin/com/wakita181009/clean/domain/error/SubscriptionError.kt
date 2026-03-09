package com.wakita181009.clean.domain.error

sealed interface SubscriptionError : DomainError {
    data class InvalidTransition(val from: String, val to: String) : SubscriptionError {
        override val message: String = "Invalid transition from $from to $to"
    }

    data object PauseLimitReached : SubscriptionError {
        override val message: String = "Pause limit reached (maximum 2 pauses per billing period)"
    }

    data object AlreadyTerminal : SubscriptionError {
        override val message: String = "Subscription is in a terminal state"
    }
}
