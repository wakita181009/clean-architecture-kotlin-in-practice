package com.wakita181009.clean.infrastructure.command.repository

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.wakita181009.clean.application.command.port.SubscriptionCommandQueryPort
import com.wakita181009.clean.domain.error.DomainError
import com.wakita181009.clean.domain.model.BillingInterval
import com.wakita181009.clean.domain.model.Currency
import com.wakita181009.clean.domain.model.CustomerId
import com.wakita181009.clean.domain.model.Discount
import com.wakita181009.clean.domain.model.DiscountId
import com.wakita181009.clean.domain.model.DiscountType
import com.wakita181009.clean.domain.model.Money
import com.wakita181009.clean.domain.model.Plan
import com.wakita181009.clean.domain.model.PlanId
import com.wakita181009.clean.domain.model.PlanTier
import com.wakita181009.clean.domain.model.Subscription
import com.wakita181009.clean.domain.model.SubscriptionId
import com.wakita181009.clean.domain.model.SubscriptionStatus
import com.wakita181009.clean.domain.repository.SubscriptionRepository
import com.wakita181009.clean.infrastructure.command.repository.SubscriptionStatusMapper.toDomainStatus
import com.wakita181009.clean.infrastructure.command.repository.SubscriptionStatusMapper.toDbStatus
import org.jooq.DSLContext
import org.jooq.generated.tables.references.DISCOUNTS
import org.jooq.generated.tables.references.PLANS
import org.jooq.generated.tables.references.SUBSCRIPTIONS
import org.springframework.stereotype.Repository
import java.time.ZoneOffset

@Repository
class SubscriptionRepositoryImpl(
    private val dsl: DSLContext,
) : SubscriptionRepository, SubscriptionCommandQueryPort {

    override fun save(subscription: Subscription): Either<DomainError, Subscription> = Either.catch {
        if (subscription.id == null) {
            val record = dsl.insertInto(SUBSCRIPTIONS)
                .set(SUBSCRIPTIONS.CUSTOMER_ID, subscription.customerId.value)
                .set(SUBSCRIPTIONS.PLAN_ID, subscription.plan.id.value)
                .set(SUBSCRIPTIONS.STATUS, toDbStatus(subscription.status))
                .set(SUBSCRIPTIONS.CURRENT_PERIOD_START, subscription.currentPeriodStart.atOffset(ZoneOffset.UTC))
                .set(SUBSCRIPTIONS.CURRENT_PERIOD_END, subscription.currentPeriodEnd.atOffset(ZoneOffset.UTC))
                .set(SUBSCRIPTIONS.TRIAL_START, subscription.trialStart?.atOffset(ZoneOffset.UTC))
                .set(SUBSCRIPTIONS.TRIAL_END, subscription.trialEnd?.atOffset(ZoneOffset.UTC))
                .set(SUBSCRIPTIONS.PAUSED_AT, subscription.pausedAt?.atOffset(ZoneOffset.UTC))
                .set(SUBSCRIPTIONS.CANCELED_AT, subscription.canceledAt?.atOffset(ZoneOffset.UTC))
                .set(SUBSCRIPTIONS.CANCEL_AT_PERIOD_END, subscription.cancelAtPeriodEnd)
                .set(SUBSCRIPTIONS.GRACE_PERIOD_END, subscription.gracePeriodEnd?.atOffset(ZoneOffset.UTC))
                .set(SUBSCRIPTIONS.PAUSE_COUNT_IN_PERIOD, subscription.pauseCountInPeriod)
                .set(SUBSCRIPTIONS.PAYMENT_METHOD, subscription.paymentMethod?.name)
                .set(SUBSCRIPTIONS.SEAT_COUNT, subscription.seatCount)
                .set(SUBSCRIPTIONS.ACCOUNT_CREDIT_BALANCE_AMOUNT, subscription.accountCreditBalance.amount)
                .set(SUBSCRIPTIONS.ACCOUNT_CREDIT_BALANCE_CURRENCY, subscription.accountCreditBalance.currency.name)
                .set(SUBSCRIPTIONS.CREATED_AT, subscription.createdAt.atOffset(ZoneOffset.UTC))
                .set(SUBSCRIPTIONS.UPDATED_AT, subscription.updatedAt.atOffset(ZoneOffset.UTC))
                .returningResult(SUBSCRIPTIONS.ID)
                .fetchOne()!!

            subscription.copy(id = SubscriptionId(record.get(SUBSCRIPTIONS.ID)!!).getOrNull()!!)
        } else {
            dsl.update(SUBSCRIPTIONS)
                .set(SUBSCRIPTIONS.PLAN_ID, subscription.plan.id.value)
                .set(SUBSCRIPTIONS.STATUS, toDbStatus(subscription.status))
                .set(SUBSCRIPTIONS.CURRENT_PERIOD_START, subscription.currentPeriodStart.atOffset(ZoneOffset.UTC))
                .set(SUBSCRIPTIONS.CURRENT_PERIOD_END, subscription.currentPeriodEnd.atOffset(ZoneOffset.UTC))
                .set(SUBSCRIPTIONS.TRIAL_START, subscription.trialStart?.atOffset(ZoneOffset.UTC))
                .set(SUBSCRIPTIONS.TRIAL_END, subscription.trialEnd?.atOffset(ZoneOffset.UTC))
                .set(SUBSCRIPTIONS.PAUSED_AT, subscription.pausedAt?.atOffset(ZoneOffset.UTC))
                .set(SUBSCRIPTIONS.CANCELED_AT, subscription.canceledAt?.atOffset(ZoneOffset.UTC))
                .set(SUBSCRIPTIONS.CANCEL_AT_PERIOD_END, subscription.cancelAtPeriodEnd)
                .set(SUBSCRIPTIONS.GRACE_PERIOD_END, subscription.gracePeriodEnd?.atOffset(ZoneOffset.UTC))
                .set(SUBSCRIPTIONS.PAUSE_COUNT_IN_PERIOD, subscription.pauseCountInPeriod)
                .set(SUBSCRIPTIONS.PAYMENT_METHOD, subscription.paymentMethod?.name)
                .set(SUBSCRIPTIONS.SEAT_COUNT, subscription.seatCount)
                .set(SUBSCRIPTIONS.ACCOUNT_CREDIT_BALANCE_AMOUNT, subscription.accountCreditBalance.amount)
                .set(SUBSCRIPTIONS.ACCOUNT_CREDIT_BALANCE_CURRENCY, subscription.accountCreditBalance.currency.name)
                .set(SUBSCRIPTIONS.UPDATED_AT, subscription.updatedAt.atOffset(ZoneOffset.UTC))
                .where(SUBSCRIPTIONS.ID.eq(subscription.id!!.value))
                .execute()
            subscription
        }
    }.mapLeft { InfraError.DatabaseError(it.message ?: "Unknown DB error") }

    override fun findById(id: SubscriptionId): Either<DomainError, Subscription> = either {
        val record = Either.catch {
            dsl.selectFrom(SUBSCRIPTIONS)
                .where(SUBSCRIPTIONS.ID.eq(id.value))
                .fetchOne()
        }.mapLeft { InfraError.DatabaseError(it.message ?: "Unknown") }.bind()

        ensureNotNull(record) { InfraError.NotFound("Subscription", id.value) }
        mapToSubscription(record).bind()
    }

    override fun findActiveByCustomerId(customerId: CustomerId): Either<DomainError, Subscription?> = either {
        val activeStatuses = listOf("TRIAL", "ACTIVE", "PAUSED", "PAST_DUE")
        val record = Either.catch {
            dsl.selectFrom(SUBSCRIPTIONS)
                .where(SUBSCRIPTIONS.CUSTOMER_ID.eq(customerId.value))
                .and(SUBSCRIPTIONS.STATUS.`in`(activeStatuses))
                .fetchOne()
        }.mapLeft { InfraError.DatabaseError(it.message ?: "Unknown") }.bind()

        if (record == null) null else mapToSubscription(record).bind()
    }

    override fun findByIdWithDiscount(id: SubscriptionId): Either<DomainError, Pair<Subscription, Discount?>> = either {
        val subscription = findById(id).bind()

        val discountRecord = Either.catch {
            dsl.selectFrom(DISCOUNTS)
                .where(DISCOUNTS.SUBSCRIPTION_ID.eq(id.value))
                .and(
                    DISCOUNTS.REMAINING_CYCLES.isNull
                        .or(DISCOUNTS.REMAINING_CYCLES.gt(0)),
                )
                .fetchOne()
        }.mapLeft { InfraError.DatabaseError(it.message ?: "Unknown") }.bind()

        val discount = if (discountRecord != null) {
            val discountId = DiscountId(discountRecord.id!!).mapLeft { it as DomainError }.bind()
            val subId = SubscriptionId(discountRecord.subscriptionId!!).mapLeft { it as DomainError }.bind()
            val type = DiscountType.valueOf(discountRecord.type!!)
            val fixedMoney = if (type == DiscountType.FIXED_AMOUNT && discountRecord.fixedAmount != null) {
                val cur = Currency.valueOf(discountRecord.fixedCurrency!!)
                Money.of(discountRecord.fixedAmount!!, cur).bind()
            } else {
                null
            }
            Discount(
                id = discountId,
                subscriptionId = subId,
                type = type,
                percentageValue = discountRecord.percentageValue?.toInt(),
                fixedAmountMoney = fixedMoney,
                durationMonths = discountRecord.durationMonths,
                remainingCycles = discountRecord.remainingCycles,
                appliedAt = discountRecord.appliedAt!!.toInstant(),
            )
        } else {
            null
        }

        Pair(subscription, discount)
    }

    private fun mapToSubscription(record: org.jooq.generated.tables.records.SubscriptionsRecord): Either<DomainError, Subscription> = either {
        val subId = SubscriptionId(record.id!!).mapLeft { it as DomainError }.bind()
        val customerId = CustomerId(record.customerId!!).mapLeft { it as DomainError }.bind()

        // Load plan
        val planRecord = Either.catch {
            dsl.selectFrom(PLANS)
                .where(PLANS.ID.eq(record.planId!!))
                .fetchOne()!!
        }.mapLeft { InfraError.DatabaseError(it.message ?: "Unknown") }.bind()

        val planId = PlanId(planRecord.id!!).mapLeft { it as DomainError }.bind()
        val currency = Currency.valueOf(planRecord.basePriceCurrency!!)
        val money = Money.of(planRecord.basePriceAmount!!, currency).bind()
        val features = planRecord.features!!.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()

        val plan = Plan.of(
            id = planId,
            name = planRecord.name!!,
            billingInterval = BillingInterval.valueOf(planRecord.billingInterval!!),
            basePrice = money,
            usageLimit = planRecord.usageLimit,
            features = features,
            tier = PlanTier.valueOf(planRecord.tier!!),
            active = planRecord.active!!,
            perSeatPricing = planRecord.perSeatPricing!!,
            minSeats = planRecord.minSeats!!,
            maxSeats = planRecord.maxSeats,
        ).mapLeft { it as DomainError }.bind()

        val status = toDomainStatus(
            record.status!!,
            record.pausedAt?.toInstant(),
            record.gracePeriodEnd?.toInstant(),
            record.canceledAt?.toInstant(),
        ).bind()

        val paymentMethod = record.paymentMethod?.let {
            Either.catch { com.wakita181009.clean.domain.model.PaymentMethod.valueOf(it) }
                .mapLeft { InfraError.UnknownValue("PaymentMethod", record.paymentMethod!!) as DomainError }
                .bind()
        }

        val accountCreditCurrency = Currency.valueOf(record.accountCreditBalanceCurrency!!)
        val accountCreditBalance = Money.of(record.accountCreditBalanceAmount!!, accountCreditCurrency).bind()

        Subscription(
            id = subId,
            customerId = customerId,
            plan = plan,
            status = status,
            currentPeriodStart = record.currentPeriodStart!!.toInstant(),
            currentPeriodEnd = record.currentPeriodEnd!!.toInstant(),
            trialStart = record.trialStart?.toInstant(),
            trialEnd = record.trialEnd?.toInstant(),
            pausedAt = record.pausedAt?.toInstant(),
            canceledAt = record.canceledAt?.toInstant(),
            cancelAtPeriodEnd = record.cancelAtPeriodEnd!!,
            gracePeriodEnd = record.gracePeriodEnd?.toInstant(),
            pauseCountInPeriod = record.pauseCountInPeriod!!,
            paymentMethod = paymentMethod,
            createdAt = record.createdAt!!.toInstant(),
            updatedAt = record.updatedAt!!.toInstant(),
            seatCount = record.seatCount,
            accountCreditBalance = accountCreditBalance,
        )
    }
}

internal object SubscriptionStatusMapper {
    fun toDbStatus(status: SubscriptionStatus): String = when (status) {
        is SubscriptionStatus.Trial -> "TRIAL"
        is SubscriptionStatus.Active -> "ACTIVE"
        is SubscriptionStatus.Paused -> "PAUSED"
        is SubscriptionStatus.PastDue -> "PAST_DUE"
        is SubscriptionStatus.Canceled -> "CANCELED"
        is SubscriptionStatus.Expired -> "EXPIRED"
    }

    fun toDomainStatus(
        dbStatus: String,
        pausedAt: java.time.Instant?,
        gracePeriodEnd: java.time.Instant?,
        canceledAt: java.time.Instant?,
    ): Either<DomainError, SubscriptionStatus> = when (dbStatus) {
        "TRIAL" -> Either.Right(SubscriptionStatus.Trial)
        "ACTIVE" -> Either.Right(SubscriptionStatus.Active)
        "PAUSED" -> Either.Right(SubscriptionStatus.Paused(pausedAt ?: java.time.Instant.EPOCH))
        "PAST_DUE" -> Either.Right(SubscriptionStatus.PastDue(gracePeriodEnd ?: java.time.Instant.EPOCH))
        "CANCELED" -> Either.Right(SubscriptionStatus.Canceled(canceledAt ?: java.time.Instant.EPOCH))
        "EXPIRED" -> Either.Right(SubscriptionStatus.Expired)
        else -> Either.Left(InfraError.UnknownValue("SubscriptionStatus", dbStatus))
    }
}

internal sealed interface InfraError : DomainError {
    data class DatabaseError(override val message: String) : InfraError
    data class NotFound(val entity: String, val id: Long) : InfraError {
        override val message: String = "$entity not found with id $id"
    }
    data class UnknownValue(val field: String, val value: String) : InfraError {
        override val message: String = "Unknown $field value: $value"
    }
}
