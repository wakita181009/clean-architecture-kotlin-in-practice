package com.wakita181009.clean.domain.model

import arrow.core.Either
import arrow.core.right
import com.wakita181009.clean.domain.error.CreditNoteError

sealed interface CreditNoteStatus {

    val name: String

    data object Issued : CreditNoteStatus {
        override val name: String = "ISSUED"

        fun apply(): Either<CreditNoteError, Applied> =
            Applied.right()

        fun void(): Either<CreditNoteError, Voided> =
            Voided.right()
    }

    data object Applied : CreditNoteStatus {
        override val name: String = "APPLIED"
    }

    data object Voided : CreditNoteStatus {
        override val name: String = "VOIDED"
    }
}
