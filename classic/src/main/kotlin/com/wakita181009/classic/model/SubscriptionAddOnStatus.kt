package com.wakita181009.classic.model

enum class SubscriptionAddOnStatus {
    ACTIVE,
    DETACHED,
    ;

    fun canTransitionTo(target: SubscriptionAddOnStatus): Boolean =
        when (this) {
            ACTIVE -> target == DETACHED
            DETACHED -> false
        }
}
