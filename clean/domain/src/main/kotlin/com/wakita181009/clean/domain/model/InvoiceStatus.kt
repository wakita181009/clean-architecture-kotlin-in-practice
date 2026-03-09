package com.wakita181009.clean.domain.model

import arrow.core.Either
import arrow.core.right
import com.wakita181009.clean.domain.error.InvoiceError
import java.time.Instant

sealed interface InvoiceStatus {

    val name: String

    data object Draft : InvoiceStatus {
        override val name: String = "DRAFT"

        fun finalize(): Either<InvoiceError, Open> =
            Open.right()

        fun void(): Either<InvoiceError, Void> =
            Void.right()
    }

    data object Open : InvoiceStatus {
        override val name: String = "OPEN"

        fun pay(paidAt: Instant): Either<InvoiceError, Paid> =
            Paid(paidAt).right()

        fun void(): Either<InvoiceError, Void> =
            Void.right()

        fun markUncollectible(): Either<InvoiceError, Uncollectible> =
            Uncollectible.right()
    }

    data class Paid(val paidAt: Instant) : InvoiceStatus {
        override val name: String = "PAID"
    }

    data object Void : InvoiceStatus {
        override val name: String = "VOID"
    }

    data object Uncollectible : InvoiceStatus {
        override val name: String = "UNCOLLECTIBLE"
    }
}
