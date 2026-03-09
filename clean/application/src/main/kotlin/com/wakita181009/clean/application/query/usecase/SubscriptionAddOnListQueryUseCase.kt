package com.wakita181009.clean.application.query.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.wakita181009.clean.application.query.dto.SubscriptionAddOnDto
import com.wakita181009.clean.application.query.error.SubscriptionAddOnListQueryError
import com.wakita181009.clean.application.query.repository.SubscriptionAddOnQueryRepository

interface SubscriptionAddOnListQueryUseCase {
    fun execute(subscriptionId: Long): Either<SubscriptionAddOnListQueryError, List<SubscriptionAddOnDto>>
}

class SubscriptionAddOnListQueryUseCaseImpl(
    private val subscriptionAddOnQueryRepository: SubscriptionAddOnQueryRepository,
) : SubscriptionAddOnListQueryUseCase {

    override fun execute(subscriptionId: Long): Either<SubscriptionAddOnListQueryError, List<SubscriptionAddOnDto>> = either {
        ensure(subscriptionId > 0) {
            SubscriptionAddOnListQueryError.InvalidInput("subscriptionId", "Must be positive")
        }
        subscriptionAddOnQueryRepository.findBySubscriptionId(subscriptionId).bind()
    }
}
