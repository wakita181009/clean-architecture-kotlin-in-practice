package com.wakita181009.clean.application.command.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.wakita181009.clean.application.command.dto.CreateSubscriptionCommand
import com.wakita181009.clean.application.command.error.SubscriptionCreateError
import com.wakita181009.clean.application.command.port.DiscountCodePort
import com.wakita181009.clean.application.command.port.PlanQueryPort
import com.wakita181009.clean.application.command.port.SubscriptionCommandQueryPort
import com.wakita181009.clean.application.port.ClockPort
import com.wakita181009.clean.application.port.TransactionPort
import com.wakita181009.clean.domain.model.CustomerId
import com.wakita181009.clean.domain.model.PaymentMethod
import com.wakita181009.clean.domain.model.PlanId
import com.wakita181009.clean.domain.model.Subscription
import com.wakita181009.clean.domain.model.SubscriptionStatus
import com.wakita181009.clean.domain.repository.DiscountRepository
import com.wakita181009.clean.domain.repository.SubscriptionRepository
import java.time.Duration

interface SubscriptionCreateUseCase {
    fun execute(command: CreateSubscriptionCommand): Either<SubscriptionCreateError, Subscription>
}

class SubscriptionCreateUseCaseImpl(
    private val subscriptionRepository: SubscriptionRepository,
    private val subscriptionCommandQueryPort: SubscriptionCommandQueryPort,
    private val planQueryPort: PlanQueryPort,
    private val discountCodePort: DiscountCodePort,
    private val discountRepository: DiscountRepository,
    private val clockPort: ClockPort,
    private val transactionPort: TransactionPort,
) : SubscriptionCreateUseCase {

    override fun execute(command: CreateSubscriptionCommand): Either<SubscriptionCreateError, Subscription> = either {
        val customerId = CustomerId(command.customerId)
            .mapLeft { SubscriptionCreateError.InvalidInput("customerId", it.message) }
            .bind()

        val planId = PlanId(command.planId)
            .mapLeft { SubscriptionCreateError.InvalidInput("planId", it.message) }
            .bind()

        val paymentMethod = Either.catch { PaymentMethod.valueOf(command.paymentMethod) }
            .mapLeft { SubscriptionCreateError.InvalidInput("paymentMethod", "Invalid payment method: ${command.paymentMethod}") }
            .bind()

        val plan = planQueryPort.findActiveById(planId)
            .mapLeft { SubscriptionCreateError.Domain(it) }
            .bind()
            .let { ensureNotNull(it) { SubscriptionCreateError.PlanNotFound } }

        val existingSubscription = subscriptionCommandQueryPort.findActiveByCustomerId(customerId)
            .mapLeft { SubscriptionCreateError.Domain(it) }
            .bind()

        ensure(existingSubscription == null) { SubscriptionCreateError.AlreadySubscribed }

        val now = clockPort.now()
        val trialEnd = now.plus(Duration.ofDays(14))

        val subscription = Subscription(
            id = null,
            customerId = customerId,
            plan = plan,
            status = SubscriptionStatus.Trial,
            currentPeriodStart = now,
            currentPeriodEnd = trialEnd,
            trialStart = now,
            trialEnd = trialEnd,
            pausedAt = null,
            canceledAt = null,
            cancelAtPeriodEnd = false,
            gracePeriodEnd = null,
            pauseCountInPeriod = 0,
            paymentMethod = paymentMethod,
            createdAt = now,
            updatedAt = now,
        )

        val savedSubscription = transactionPort.run {
            val saved = subscriptionRepository.save(subscription)
                .mapLeft { SubscriptionCreateError.Domain(it) }

            if (saved.isRight() && command.discountCode != null) {
                val sub = saved.getOrNull()!!
                val subId = sub.id!!
                val discount = discountCodePort.resolve(command.discountCode, subId, now)
                    .mapLeft { SubscriptionCreateError.InvalidDiscountCode }
                if (discount.isRight()) {
                    val d = discount.getOrNull()
                    if (d != null) {
                        discountRepository.save(d)
                            .mapLeft { SubscriptionCreateError.Domain(it) }
                    }
                } else {
                    return@run discount.mapLeft { it as SubscriptionCreateError }
                        .map { sub }
                }
            }

            saved
        }

        savedSubscription.bind()
    }
}
