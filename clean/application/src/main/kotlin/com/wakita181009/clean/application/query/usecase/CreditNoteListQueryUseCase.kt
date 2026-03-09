package com.wakita181009.clean.application.query.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.wakita181009.clean.application.query.dto.CreditNoteDto
import com.wakita181009.clean.application.query.error.CreditNoteListQueryError
import com.wakita181009.clean.application.query.repository.CreditNoteQueryRepository

interface CreditNoteListQueryUseCase {
    fun execute(invoiceId: Long): Either<CreditNoteListQueryError, List<CreditNoteDto>>
}

class CreditNoteListQueryUseCaseImpl(
    private val creditNoteQueryRepository: CreditNoteQueryRepository,
) : CreditNoteListQueryUseCase {

    override fun execute(invoiceId: Long): Either<CreditNoteListQueryError, List<CreditNoteDto>> = either {
        ensure(invoiceId > 0) {
            CreditNoteListQueryError.InvalidInput("invoiceId", "Must be positive")
        }
        creditNoteQueryRepository.findByInvoiceId(invoiceId).bind()
    }
}
