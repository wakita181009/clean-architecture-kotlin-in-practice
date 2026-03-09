package com.wakita181009.clean.application.query.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.wakita181009.clean.application.query.dto.SubscriptionDto
import com.wakita181009.clean.application.query.error.SubscriptionListByCustomerQueryError
import com.wakita181009.clean.application.query.repository.SubscriptionQueryRepository

interface SubscriptionListByCustomerQueryUseCase {
    fun execute(customerId: Long): Either<SubscriptionListByCustomerQueryError, List<SubscriptionDto>>
}

class SubscriptionListByCustomerQueryUseCaseImpl(
    private val subscriptionQueryRepository: SubscriptionQueryRepository,
) : SubscriptionListByCustomerQueryUseCase {

    override fun execute(customerId: Long): Either<SubscriptionListByCustomerQueryError, List<SubscriptionDto>> = either {
        ensure(customerId > 0) { SubscriptionListByCustomerQueryError.InvalidInput("customerId", "must be positive") }
        subscriptionQueryRepository.listByCustomerId(customerId).bind()
    }
}
