package com.wakita181009.clean.infrastructure.query.repository

import arrow.core.Either
import com.wakita181009.clean.application.query.dto.SubscriptionAddOnDto
import com.wakita181009.clean.application.query.error.SubscriptionAddOnListQueryError
import com.wakita181009.clean.application.query.repository.SubscriptionAddOnQueryRepository
import org.jooq.DSLContext
import org.jooq.generated.tables.references.ADDONS
import org.jooq.generated.tables.references.SUBSCRIPTION_ADDONS
import org.springframework.stereotype.Repository

@Repository
class SubscriptionAddOnQueryRepositoryImpl(
    private val dsl: DSLContext,
) : SubscriptionAddOnQueryRepository {

    override fun findBySubscriptionId(subscriptionId: Long): Either<SubscriptionAddOnListQueryError, List<SubscriptionAddOnDto>> =
        Either.catch {
            dsl.select(
                SUBSCRIPTION_ADDONS.ID,
                SUBSCRIPTION_ADDONS.SUBSCRIPTION_ID,
                SUBSCRIPTION_ADDONS.ADDON_ID,
                ADDONS.NAME,
                ADDONS.PRICE_AMOUNT,
                ADDONS.PRICE_CURRENCY,
                ADDONS.BILLING_TYPE,
                SUBSCRIPTION_ADDONS.QUANTITY,
                SUBSCRIPTION_ADDONS.STATUS,
                SUBSCRIPTION_ADDONS.ATTACHED_AT,
                SUBSCRIPTION_ADDONS.DETACHED_AT,
            )
                .from(SUBSCRIPTION_ADDONS)
                .join(ADDONS).on(SUBSCRIPTION_ADDONS.ADDON_ID.eq(ADDONS.ID))
                .where(SUBSCRIPTION_ADDONS.SUBSCRIPTION_ID.eq(subscriptionId))
                .fetch()
                .map { record ->
                    SubscriptionAddOnDto(
                        id = record.get(SUBSCRIPTION_ADDONS.ID)!!,
                        subscriptionId = record.get(SUBSCRIPTION_ADDONS.SUBSCRIPTION_ID)!!,
                        addOnId = record.get(SUBSCRIPTION_ADDONS.ADDON_ID)!!,
                        addOnName = record.get(ADDONS.NAME)!!,
                        addOnPriceAmount = record.get(ADDONS.PRICE_AMOUNT)!!.toPlainString(),
                        addOnPriceCurrency = record.get(ADDONS.PRICE_CURRENCY)!!,
                        addOnBillingType = record.get(ADDONS.BILLING_TYPE)!!,
                        quantity = record.get(SUBSCRIPTION_ADDONS.QUANTITY)!!,
                        status = record.get(SUBSCRIPTION_ADDONS.STATUS)!!,
                        attachedAt = record.get(SUBSCRIPTION_ADDONS.ATTACHED_AT)!!.toInstant(),
                        detachedAt = record.get(SUBSCRIPTION_ADDONS.DETACHED_AT)?.toInstant(),
                    )
                }
        }.mapLeft { SubscriptionAddOnListQueryError.Internal(it.message ?: "Unknown error") }
}
