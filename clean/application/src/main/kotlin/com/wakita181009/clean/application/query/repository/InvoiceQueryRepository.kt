package com.wakita181009.clean.application.query.repository

import arrow.core.Either
import com.wakita181009.clean.application.query.dto.InvoiceDto
import com.wakita181009.clean.application.query.error.InvoiceListBySubscriptionQueryError

interface InvoiceQueryRepository {
    fun listBySubscriptionId(subscriptionId: Long): Either<InvoiceListBySubscriptionQueryError, List<InvoiceDto>>
}
