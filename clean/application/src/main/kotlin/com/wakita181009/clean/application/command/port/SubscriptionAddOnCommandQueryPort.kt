package com.wakita181009.clean.application.command.port

import arrow.core.Either
import com.wakita181009.clean.domain.error.DomainError
import com.wakita181009.clean.domain.model.AddOnId
import com.wakita181009.clean.domain.model.SubscriptionAddOn
import com.wakita181009.clean.domain.model.SubscriptionId

interface SubscriptionAddOnCommandQueryPort {
    fun findActiveBySubscriptionId(subscriptionId: SubscriptionId): Either<DomainError, List<SubscriptionAddOn>>
    fun findActiveBySubscriptionIdAndAddOnId(subscriptionId: SubscriptionId, addOnId: AddOnId): Either<DomainError, SubscriptionAddOn?>
}
