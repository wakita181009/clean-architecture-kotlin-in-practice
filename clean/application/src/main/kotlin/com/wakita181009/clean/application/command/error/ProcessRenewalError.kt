package com.wakita181009.clean.application.command.error

import com.wakita181009.clean.application.error.ApplicationError
import com.wakita181009.clean.domain.error.DomainError

sealed interface ProcessRenewalError : ApplicationError {
    data object SubscriptionNotFound : ProcessRenewalError {
        override val message: String = "Subscription not found"
    }

    data object NotDue : ProcessRenewalError {
        override val message: String = "Subscription period has not ended or not in Active status"
    }

    data class Domain(val error: DomainError) : ProcessRenewalError {
        override val message: String = error.message
    }

    data class Internal(val cause: String) : ProcessRenewalError {
        override val message: String = "Internal error: $cause"
    }
}
