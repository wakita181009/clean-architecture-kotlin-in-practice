package com.wakita181009.clean.application.command.error

import com.wakita181009.clean.application.error.ApplicationError
import com.wakita181009.clean.domain.error.DomainError

sealed interface AttachAddOnError : ApplicationError {
    data class InvalidInput(val field: String, val reason: String) : AttachAddOnError {
        override val message: String = "Invalid input: $field - $reason"
    }

    data object SubscriptionNotFound : AttachAddOnError {
        override val message: String = "Subscription not found"
    }

    data object NotActive : AttachAddOnError {
        override val message: String = "Subscription must be in Active status"
    }

    data object AddOnNotFound : AttachAddOnError {
        override val message: String = "Add-on not found or inactive"
    }

    data object CurrencyMismatch : AttachAddOnError {
        override val message: String = "Add-on currency must match subscription's plan currency"
    }

    data object TierIncompatible : AttachAddOnError {
        override val message: String = "Add-on is not compatible with subscription's plan tier"
    }

    data object PerSeatOnNonPerSeatPlan : AttachAddOnError {
        override val message: String = "PER_SEAT add-on can only be attached to per-seat plan subscriptions"
    }

    data object DuplicateAddOn : AttachAddOnError {
        override val message: String = "This add-on is already attached to the subscription"
    }

    data object AddOnLimitReached : AttachAddOnError {
        override val message: String = "Maximum 5 active add-ons per subscription"
    }

    data class PaymentFailed(val reason: String) : AttachAddOnError {
        override val message: String = "Payment failed: $reason"
    }

    data class Domain(val error: DomainError) : AttachAddOnError {
        override val message: String = error.message
    }

    data class Internal(val cause: String) : AttachAddOnError {
        override val message: String = "Internal error: $cause"
    }
}
