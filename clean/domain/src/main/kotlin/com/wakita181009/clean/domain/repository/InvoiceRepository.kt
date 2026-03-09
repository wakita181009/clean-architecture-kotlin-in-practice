package com.wakita181009.clean.domain.repository

import arrow.core.Either
import com.wakita181009.clean.domain.error.DomainError
import com.wakita181009.clean.domain.model.Invoice

interface InvoiceRepository {
    fun save(invoice: Invoice): Either<DomainError, Invoice>
}
