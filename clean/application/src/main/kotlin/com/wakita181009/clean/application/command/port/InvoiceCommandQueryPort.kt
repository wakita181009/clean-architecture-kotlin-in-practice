package com.wakita181009.clean.application.command.port

import arrow.core.Either
import com.wakita181009.clean.domain.error.DomainError
import com.wakita181009.clean.domain.model.Invoice
import com.wakita181009.clean.domain.model.InvoiceId
import com.wakita181009.clean.domain.model.SubscriptionId

interface InvoiceCommandQueryPort {
    fun findById(id: InvoiceId): Either<DomainError, Invoice>
    fun findOpenBySubscriptionId(subscriptionId: SubscriptionId): Either<DomainError, List<Invoice>>
}
