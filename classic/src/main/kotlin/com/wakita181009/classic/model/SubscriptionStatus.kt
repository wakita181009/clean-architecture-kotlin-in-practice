package com.wakita181009.classic.model

enum class SubscriptionStatus {
    TRIAL,
    ACTIVE,
    PAUSED,
    PAST_DUE,
    CANCELED,
    EXPIRED,
    ;

    fun canTransitionTo(target: SubscriptionStatus): Boolean =
        when (this) {
            TRIAL -> target in setOf(ACTIVE, CANCELED, EXPIRED)
            ACTIVE -> target in setOf(PAUSED, PAST_DUE, CANCELED)
            PAUSED -> target in setOf(ACTIVE, CANCELED)
            PAST_DUE -> target in setOf(ACTIVE, CANCELED)
            CANCELED -> false
            EXPIRED -> false
        }
}
