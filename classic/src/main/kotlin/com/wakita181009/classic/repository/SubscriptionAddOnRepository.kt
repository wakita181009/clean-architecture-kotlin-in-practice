package com.wakita181009.classic.repository

import com.wakita181009.classic.model.SubscriptionAddOn
import com.wakita181009.classic.model.SubscriptionAddOnStatus
import org.springframework.data.jpa.repository.JpaRepository

interface SubscriptionAddOnRepository : JpaRepository<SubscriptionAddOn, Long> {
    fun findBySubscriptionIdAndAddOnIdAndStatus(
        subscriptionId: Long,
        addOnId: Long,
        status: SubscriptionAddOnStatus,
    ): SubscriptionAddOn?

    fun findBySubscriptionIdAndStatus(
        subscriptionId: Long,
        status: SubscriptionAddOnStatus,
    ): List<SubscriptionAddOn>

    fun findBySubscriptionId(subscriptionId: Long): List<SubscriptionAddOn>

    fun countBySubscriptionIdAndStatus(
        subscriptionId: Long,
        status: SubscriptionAddOnStatus,
    ): Long
}
