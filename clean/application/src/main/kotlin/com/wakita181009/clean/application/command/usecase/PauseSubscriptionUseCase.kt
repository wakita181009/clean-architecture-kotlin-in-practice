package com.wakita181009.clean.application.command.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.wakita181009.clean.application.command.error.PauseSubscriptionError
import com.wakita181009.clean.application.command.port.SubscriptionCommandQueryPort
import com.wakita181009.clean.application.port.ClockPort
import com.wakita181009.clean.application.port.TransactionPort
import com.wakita181009.clean.domain.model.Subscription
import com.wakita181009.clean.domain.model.SubscriptionId
import com.wakita181009.clean.domain.model.SubscriptionStatus
import com.wakita181009.clean.domain.repository.SubscriptionRepository

interface PauseSubscriptionUseCase {
    fun execute(subscriptionId: Long): Either<PauseSubscriptionError, Subscription>
}

class PauseSubscriptionUseCaseImpl(
    private val subscriptionCommandQueryPort: SubscriptionCommandQueryPort,
    private val subscriptionRepository: SubscriptionRepository,
    private val clockPort: ClockPort,
    private val transactionPort: TransactionPort,
) : PauseSubscriptionUseCase {

    override fun execute(subscriptionId: Long): Either<PauseSubscriptionError, Subscription> = either {
        val subId = SubscriptionId(subscriptionId)
            .mapLeft { PauseSubscriptionError.InvalidInput("subscriptionId", it.message) }
            .bind()

        val subscription = subscriptionCommandQueryPort.findById(subId)
            .mapLeft { PauseSubscriptionError.SubscriptionNotFound }
            .bind()

        val activeStatus = subscription.status
        ensure(activeStatus is SubscriptionStatus.Active) { PauseSubscriptionError.NotActive }

        val now = clockPort.now()
        val pausedStatus = activeStatus.pause(now, subscription.pauseCountInPeriod)
            .mapLeft {
                when (it) {
                    is com.wakita181009.clean.domain.error.SubscriptionError.PauseLimitReached ->
                        PauseSubscriptionError.PauseLimitReached
                    else -> PauseSubscriptionError.Domain(it)
                }
            }
            .bind()

        val updatedSubscription = subscription.copy(
            status = pausedStatus,
            pausedAt = now,
            pauseCountInPeriod = subscription.pauseCountInPeriod + 1,
            updatedAt = now,
        )

        transactionPort.run {
            subscriptionRepository.save(updatedSubscription)
                .mapLeft { PauseSubscriptionError.Domain(it) }
        }.bind()
    }
}
