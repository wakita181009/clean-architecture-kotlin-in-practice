package com.wakita181009.clean.domain.repository

import arrow.core.Either
import com.wakita181009.clean.domain.error.DomainError
import com.wakita181009.clean.domain.model.CreditNote

interface CreditNoteRepository {
    fun save(creditNote: CreditNote): Either<DomainError, CreditNote>
}
