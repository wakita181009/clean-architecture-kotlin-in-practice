package com.wakita181009.clean.application.command.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.wakita181009.clean.application.command.dto.RecordUsageCommand
import com.wakita181009.clean.application.command.error.RecordUsageError
import com.wakita181009.clean.application.command.port.SubscriptionCommandQueryPort
import com.wakita181009.clean.application.command.port.UsageQueryPort
import com.wakita181009.clean.application.port.ClockPort
import com.wakita181009.clean.application.port.TransactionPort
import com.wakita181009.clean.domain.model.IdempotencyKey
import com.wakita181009.clean.domain.model.MetricName
import com.wakita181009.clean.domain.model.SubscriptionId
import com.wakita181009.clean.domain.model.SubscriptionStatus
import com.wakita181009.clean.domain.model.UsageRecord
import com.wakita181009.clean.domain.repository.UsageRecordRepository

interface RecordUsageUseCase {
    fun execute(command: RecordUsageCommand): Either<RecordUsageError, UsageRecord>
}

class RecordUsageUseCaseImpl(
    private val subscriptionCommandQueryPort: SubscriptionCommandQueryPort,
    private val usageQueryPort: UsageQueryPort,
    private val usageRecordRepository: UsageRecordRepository,
    private val clockPort: ClockPort,
    private val transactionPort: TransactionPort,
) : RecordUsageUseCase {

    override fun execute(command: RecordUsageCommand): Either<RecordUsageError, UsageRecord> = either {
        val subId = SubscriptionId(command.subscriptionId)
            .mapLeft { RecordUsageError.InvalidInput("subscriptionId", it.message) }
            .bind()

        ensure(command.quantity >= 1) {
            RecordUsageError.InvalidInput("quantity", "must be at least 1")
        }

        val metricName = MetricName(command.metricName)
            .mapLeft { RecordUsageError.InvalidInput("metricName", it.message) }
            .bind()

        val idempotencyKey = IdempotencyKey(command.idempotencyKey)
            .mapLeft { RecordUsageError.InvalidInput("idempotencyKey", it.message) }
            .bind()

        // Check idempotency
        val existing = usageQueryPort.findByIdempotencyKey(idempotencyKey)
            .mapLeft { RecordUsageError.Domain(it) }
            .bind()

        if (existing != null) {
            return@either existing
        }

        val subscription = subscriptionCommandQueryPort.findById(subId)
            .mapLeft { RecordUsageError.SubscriptionNotFound }
            .bind()

        ensure(subscription.status is SubscriptionStatus.Active) { RecordUsageError.NotActive }

        // Check usage limit
        val usageLimit = subscription.plan.usageLimit
        if (usageLimit != null) {
            val subId2 = subscription.id!!
            val currentUsage = usageQueryPort.sumQuantityForPeriod(
                subId2,
                subscription.currentPeriodStart,
                subscription.currentPeriodEnd,
            ).mapLeft { RecordUsageError.Domain(it) }.bind()

            ensure(currentUsage + command.quantity <= usageLimit) {
                RecordUsageError.UsageLimitExceeded(currentUsage, usageLimit, command.quantity)
            }
        }

        val now = clockPort.now()
        val usageRecord = UsageRecord(
            id = null,
            subscriptionId = subscription.id!!,
            metricName = metricName,
            quantity = command.quantity,
            recordedAt = now,
            idempotencyKey = idempotencyKey,
        )

        transactionPort.run {
            usageRecordRepository.save(usageRecord)
                .mapLeft { RecordUsageError.Domain(it) }
        }.bind()
    }
}
