package com.wakita181009.clean.application.command.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.wakita181009.clean.application.command.error.ResumeSubscriptionError
import com.wakita181009.clean.application.command.port.SubscriptionCommandQueryPort
import com.wakita181009.clean.application.port.ClockPort
import com.wakita181009.clean.application.port.TransactionPort
import com.wakita181009.clean.domain.model.Subscription
import com.wakita181009.clean.domain.model.SubscriptionId
import com.wakita181009.clean.domain.model.SubscriptionStatus
import com.wakita181009.clean.domain.repository.SubscriptionRepository
import java.time.Duration

interface ResumeSubscriptionUseCase {
    fun execute(subscriptionId: Long): Either<ResumeSubscriptionError, Subscription>
}

class ResumeSubscriptionUseCaseImpl(
    private val subscriptionCommandQueryPort: SubscriptionCommandQueryPort,
    private val subscriptionRepository: SubscriptionRepository,
    private val clockPort: ClockPort,
    private val transactionPort: TransactionPort,
) : ResumeSubscriptionUseCase {

    override fun execute(subscriptionId: Long): Either<ResumeSubscriptionError, Subscription> = either {
        val subId = SubscriptionId(subscriptionId)
            .mapLeft { ResumeSubscriptionError.InvalidInput("subscriptionId", it.message) }
            .bind()

        val subscription = subscriptionCommandQueryPort.findById(subId)
            .mapLeft { ResumeSubscriptionError.SubscriptionNotFound }
            .bind()

        val pausedStatus = subscription.status
        ensure(pausedStatus is SubscriptionStatus.Paused) { ResumeSubscriptionError.NotPaused }

        val pausedAt = ensureNotNull(subscription.pausedAt) { ResumeSubscriptionError.NotPaused }
        val now = clockPort.now()

        // Calculate remaining days from when it was paused
        val remainingDays = Duration.between(pausedAt, subscription.currentPeriodEnd).toDays().coerceAtLeast(0)
        val newPeriodEnd = now.plus(Duration.ofDays(remainingDays))

        val activeStatus = pausedStatus.resume()
            .mapLeft { ResumeSubscriptionError.Domain(it) }
            .bind()

        val updatedSubscription = subscription.copy(
            status = activeStatus,
            currentPeriodEnd = newPeriodEnd,
            pausedAt = null,
            updatedAt = now,
        )

        transactionPort.run {
            subscriptionRepository.save(updatedSubscription)
                .mapLeft { ResumeSubscriptionError.Domain(it) }
        }.bind()
    }
}
