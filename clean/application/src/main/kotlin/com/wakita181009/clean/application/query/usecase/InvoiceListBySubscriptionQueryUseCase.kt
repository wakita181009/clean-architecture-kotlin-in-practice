package com.wakita181009.clean.application.query.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.wakita181009.clean.application.query.dto.InvoiceDto
import com.wakita181009.clean.application.query.error.InvoiceListBySubscriptionQueryError
import com.wakita181009.clean.application.query.repository.InvoiceQueryRepository

interface InvoiceListBySubscriptionQueryUseCase {
    fun execute(subscriptionId: Long): Either<InvoiceListBySubscriptionQueryError, List<InvoiceDto>>
}

class InvoiceListBySubscriptionQueryUseCaseImpl(
    private val invoiceQueryRepository: InvoiceQueryRepository,
) : InvoiceListBySubscriptionQueryUseCase {

    override fun execute(subscriptionId: Long): Either<InvoiceListBySubscriptionQueryError, List<InvoiceDto>> = either {
        ensure(subscriptionId > 0) { InvoiceListBySubscriptionQueryError.InvalidInput("subscriptionId", "must be positive") }
        invoiceQueryRepository.listBySubscriptionId(subscriptionId).bind()
    }
}
