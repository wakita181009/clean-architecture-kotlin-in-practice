package com.wakita181009.clean.application.command.error

import com.wakita181009.clean.application.error.ApplicationError
import com.wakita181009.clean.domain.error.DomainError

sealed interface RecoverPaymentError : ApplicationError {
    data class InvalidInput(val field: String, val reason: String) : RecoverPaymentError {
        override val message: String = "Invalid input: $field - $reason"
    }

    data object InvoiceNotFound : RecoverPaymentError {
        override val message: String = "Invoice not found"
    }

    data object InvoiceNotOpen : RecoverPaymentError {
        override val message: String = "Invoice must be in Open status"
    }

    data object SubscriptionNotPastDue : RecoverPaymentError {
        override val message: String = "Subscription must be in PastDue status"
    }

    data object GracePeriodExpired : RecoverPaymentError {
        override val message: String = "Grace period has expired"
    }

    data class PaymentFailed(val reason: String) : RecoverPaymentError {
        override val message: String = "Payment failed: $reason"
    }

    data class Domain(val error: DomainError) : RecoverPaymentError {
        override val message: String = error.message
    }

    data class Internal(val cause: String) : RecoverPaymentError {
        override val message: String = "Internal error: $cause"
    }
}
