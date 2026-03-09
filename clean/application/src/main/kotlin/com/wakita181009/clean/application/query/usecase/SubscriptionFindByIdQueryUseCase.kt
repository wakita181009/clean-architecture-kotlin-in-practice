package com.wakita181009.clean.application.query.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.wakita181009.clean.application.query.dto.SubscriptionDto
import com.wakita181009.clean.application.query.error.SubscriptionFindByIdQueryError
import com.wakita181009.clean.application.query.repository.SubscriptionQueryRepository

interface SubscriptionFindByIdQueryUseCase {
    fun execute(id: Long): Either<SubscriptionFindByIdQueryError, SubscriptionDto>
}

class SubscriptionFindByIdQueryUseCaseImpl(
    private val subscriptionQueryRepository: SubscriptionQueryRepository,
) : SubscriptionFindByIdQueryUseCase {

    override fun execute(id: Long): Either<SubscriptionFindByIdQueryError, SubscriptionDto> = either {
        ensure(id > 0) { SubscriptionFindByIdQueryError.InvalidInput("id", "must be positive") }
        subscriptionQueryRepository.findById(id).bind()
    }
}
