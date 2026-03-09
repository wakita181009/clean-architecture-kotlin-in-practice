package com.wakita181009.clean.application.command.error

import com.wakita181009.clean.application.error.ApplicationError
import com.wakita181009.clean.domain.error.DomainError

sealed interface UpdateSeatCountError : ApplicationError {
    data class InvalidInput(val field: String, val reason: String) : UpdateSeatCountError {
        override val message: String = "Invalid input: $field - $reason"
    }

    data object SubscriptionNotFound : UpdateSeatCountError {
        override val message: String = "Subscription not found"
    }

    data object NotActive : UpdateSeatCountError {
        override val message: String = "Subscription must be in Active status"
    }

    data object NotPerSeatPlan : UpdateSeatCountError {
        override val message: String = "Subscription is not on a per-seat plan"
    }

    data object SameSeatCount : UpdateSeatCountError {
        override val message: String = "New seat count is the same as current seat count"
    }

    data object BelowMinimum : UpdateSeatCountError {
        override val message: String = "Seat count is below the plan's minimum"
    }

    data object AboveMaximum : UpdateSeatCountError {
        override val message: String = "Seat count is above the plan's maximum"
    }

    data class PaymentFailed(val reason: String) : UpdateSeatCountError {
        override val message: String = "Payment failed: $reason"
    }

    data class Domain(val error: DomainError) : UpdateSeatCountError {
        override val message: String = error.message
    }

    data class Internal(val cause: String) : UpdateSeatCountError {
        override val message: String = "Internal error: $cause"
    }
}
