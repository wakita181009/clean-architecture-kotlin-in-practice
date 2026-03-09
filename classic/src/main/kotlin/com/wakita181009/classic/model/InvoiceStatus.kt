package com.wakita181009.classic.model

enum class InvoiceStatus {
    DRAFT,
    OPEN,
    PAID,
    VOID,
    UNCOLLECTIBLE,
    ;

    fun canTransitionTo(target: InvoiceStatus): Boolean =
        when (this) {
            DRAFT -> target in setOf(OPEN, VOID)
            OPEN -> target in setOf(PAID, VOID, UNCOLLECTIBLE)
            PAID -> false
            VOID -> false
            UNCOLLECTIBLE -> false
        }
}
