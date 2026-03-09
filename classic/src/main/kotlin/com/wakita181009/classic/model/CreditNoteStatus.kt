package com.wakita181009.classic.model

enum class CreditNoteStatus {
    ISSUED,
    APPLIED,
    VOIDED,
    ;

    fun canTransitionTo(target: CreditNoteStatus): Boolean =
        when (this) {
            ISSUED -> target in setOf(APPLIED, VOIDED)
            APPLIED -> false
            VOIDED -> false
        }
}
