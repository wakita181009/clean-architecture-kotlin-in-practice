package com.wakita181009.clean.application.command.port

import arrow.core.Either
import com.wakita181009.clean.domain.error.DomainError
import com.wakita181009.clean.domain.model.CreditNote
import com.wakita181009.clean.domain.model.InvoiceId

interface CreditNoteCommandQueryPort {
    fun findByInvoiceId(invoiceId: InvoiceId): Either<DomainError, List<CreditNote>>
}
