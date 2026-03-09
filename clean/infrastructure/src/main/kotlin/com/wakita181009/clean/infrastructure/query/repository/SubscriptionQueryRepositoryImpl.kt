package com.wakita181009.clean.infrastructure.query.repository

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.wakita181009.clean.application.query.dto.SubscriptionDto
import com.wakita181009.clean.application.query.error.SubscriptionFindByIdQueryError
import com.wakita181009.clean.application.query.error.SubscriptionListByCustomerQueryError
import com.wakita181009.clean.application.query.repository.SubscriptionQueryRepository
import org.jooq.DSLContext
import org.jooq.generated.tables.references.DISCOUNTS
import org.jooq.generated.tables.references.PLANS
import org.jooq.generated.tables.references.SUBSCRIPTIONS
import org.springframework.stereotype.Repository

@Repository
class SubscriptionQueryRepositoryImpl(
    private val dsl: DSLContext,
) : SubscriptionQueryRepository {

    override fun findById(id: Long): Either<SubscriptionFindByIdQueryError, SubscriptionDto> = either {
        val record = Either.catch {
            dsl.select()
                .from(SUBSCRIPTIONS)
                .join(PLANS).on(SUBSCRIPTIONS.PLAN_ID.eq(PLANS.ID))
                .leftJoin(DISCOUNTS).on(
                    DISCOUNTS.SUBSCRIPTION_ID.eq(SUBSCRIPTIONS.ID)
                        .and(
                            DISCOUNTS.REMAINING_CYCLES.isNull
                                .or(DISCOUNTS.REMAINING_CYCLES.gt(0)),
                        ),
                )
                .where(SUBSCRIPTIONS.ID.eq(id))
                .fetchOne()
        }.mapLeft { SubscriptionFindByIdQueryError.Internal(it.message ?: "Unknown") }.bind()

        ensureNotNull(record) { SubscriptionFindByIdQueryError.NotFound }

        SubscriptionDto(
            id = record.get(SUBSCRIPTIONS.ID)!!,
            customerId = record.get(SUBSCRIPTIONS.CUSTOMER_ID)!!,
            planId = record.get(PLANS.ID)!!,
            planName = record.get(PLANS.NAME)!!,
            planTier = record.get(PLANS.TIER)!!,
            planBillingInterval = record.get(PLANS.BILLING_INTERVAL)!!,
            planBasePriceAmount = record.get(PLANS.BASE_PRICE_AMOUNT)!!.toPlainString(),
            planBasePriceCurrency = record.get(PLANS.BASE_PRICE_CURRENCY)!!,
            status = record.get(SUBSCRIPTIONS.STATUS)!!,
            currentPeriodStart = record.get(SUBSCRIPTIONS.CURRENT_PERIOD_START)!!.toInstant(),
            currentPeriodEnd = record.get(SUBSCRIPTIONS.CURRENT_PERIOD_END)!!.toInstant(),
            trialEnd = record.get(SUBSCRIPTIONS.TRIAL_END)?.toInstant(),
            pausedAt = record.get(SUBSCRIPTIONS.PAUSED_AT)?.toInstant(),
            canceledAt = record.get(SUBSCRIPTIONS.CANCELED_AT)?.toInstant(),
            cancelAtPeriodEnd = record.get(SUBSCRIPTIONS.CANCEL_AT_PERIOD_END)!!,
            discountType = record.get(DISCOUNTS.TYPE),
            discountValue = record.get(DISCOUNTS.PERCENTAGE_VALUE)?.toPlainString()
                ?: record.get(DISCOUNTS.FIXED_AMOUNT)?.toPlainString(),
            discountRemainingCycles = record.get(DISCOUNTS.REMAINING_CYCLES),
            createdAt = record.get(SUBSCRIPTIONS.CREATED_AT)!!.toInstant(),
            updatedAt = record.get(SUBSCRIPTIONS.UPDATED_AT)!!.toInstant(),
            seatCount = record.get(SUBSCRIPTIONS.SEAT_COUNT),
            accountCreditBalanceAmount = record.get(SUBSCRIPTIONS.ACCOUNT_CREDIT_BALANCE_AMOUNT)!!.toPlainString(),
            accountCreditBalanceCurrency = record.get(SUBSCRIPTIONS.ACCOUNT_CREDIT_BALANCE_CURRENCY)!!,
        )
    }

    override fun listByCustomerId(customerId: Long): Either<SubscriptionListByCustomerQueryError, List<SubscriptionDto>> = either {
        val records = Either.catch {
            dsl.select()
                .from(SUBSCRIPTIONS)
                .join(PLANS).on(SUBSCRIPTIONS.PLAN_ID.eq(PLANS.ID))
                .leftJoin(DISCOUNTS).on(
                    DISCOUNTS.SUBSCRIPTION_ID.eq(SUBSCRIPTIONS.ID)
                        .and(
                            DISCOUNTS.REMAINING_CYCLES.isNull
                                .or(DISCOUNTS.REMAINING_CYCLES.gt(0)),
                        ),
                )
                .where(SUBSCRIPTIONS.CUSTOMER_ID.eq(customerId))
                .fetch()
        }.mapLeft { SubscriptionListByCustomerQueryError.Internal(it.message ?: "Unknown") }.bind()

        records.map { record ->
            SubscriptionDto(
                id = record.get(SUBSCRIPTIONS.ID)!!,
                customerId = record.get(SUBSCRIPTIONS.CUSTOMER_ID)!!,
                planId = record.get(PLANS.ID)!!,
                planName = record.get(PLANS.NAME)!!,
                planTier = record.get(PLANS.TIER)!!,
                planBillingInterval = record.get(PLANS.BILLING_INTERVAL)!!,
                planBasePriceAmount = record.get(PLANS.BASE_PRICE_AMOUNT)!!.toPlainString(),
                planBasePriceCurrency = record.get(PLANS.BASE_PRICE_CURRENCY)!!,
                status = record.get(SUBSCRIPTIONS.STATUS)!!,
                currentPeriodStart = record.get(SUBSCRIPTIONS.CURRENT_PERIOD_START)!!.toInstant(),
                currentPeriodEnd = record.get(SUBSCRIPTIONS.CURRENT_PERIOD_END)!!.toInstant(),
                trialEnd = record.get(SUBSCRIPTIONS.TRIAL_END)?.toInstant(),
                pausedAt = record.get(SUBSCRIPTIONS.PAUSED_AT)?.toInstant(),
                canceledAt = record.get(SUBSCRIPTIONS.CANCELED_AT)?.toInstant(),
                cancelAtPeriodEnd = record.get(SUBSCRIPTIONS.CANCEL_AT_PERIOD_END)!!,
                discountType = record.get(DISCOUNTS.TYPE),
                discountValue = record.get(DISCOUNTS.PERCENTAGE_VALUE)?.toPlainString()
                    ?: record.get(DISCOUNTS.FIXED_AMOUNT)?.toPlainString(),
                discountRemainingCycles = record.get(DISCOUNTS.REMAINING_CYCLES),
                createdAt = record.get(SUBSCRIPTIONS.CREATED_AT)!!.toInstant(),
                updatedAt = record.get(SUBSCRIPTIONS.UPDATED_AT)!!.toInstant(),
                seatCount = record.get(SUBSCRIPTIONS.SEAT_COUNT),
                accountCreditBalanceAmount = record.get(SUBSCRIPTIONS.ACCOUNT_CREDIT_BALANCE_AMOUNT)!!.toPlainString(),
                accountCreditBalanceCurrency = record.get(SUBSCRIPTIONS.ACCOUNT_CREDIT_BALANCE_CURRENCY)!!,
            )
        }
    }
}
