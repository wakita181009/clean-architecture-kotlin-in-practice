package com.wakita181009.clean.domain.error

sealed interface CreditNoteError : DomainError {
    data object AmountMustBePositive : CreditNoteError {
        override val message: String = "Credit note amount must be greater than zero"
    }

    data object BlankReason : CreditNoteError {
        override val message: String = "Credit note reason must not be blank"
    }

    data object AlreadyTerminal : CreditNoteError {
        override val message: String = "Credit note is in a terminal state"
    }

    data object NoSelfTransition : CreditNoteError {
        override val message: String = "Cannot transition to the same state"
    }
}
