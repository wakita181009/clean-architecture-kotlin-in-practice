package com.wakita181009.clean.application.query.repository

import arrow.core.Either
import com.wakita181009.clean.application.query.dto.CreditNoteDto
import com.wakita181009.clean.application.query.error.CreditNoteListQueryError

interface CreditNoteQueryRepository {
    fun findByInvoiceId(invoiceId: Long): Either<CreditNoteListQueryError, List<CreditNoteDto>>
}
