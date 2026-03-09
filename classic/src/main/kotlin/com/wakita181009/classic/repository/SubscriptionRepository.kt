package com.wakita181009.classic.repository

import com.wakita181009.classic.model.Subscription
import com.wakita181009.classic.model.SubscriptionStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SubscriptionRepository : JpaRepository<Subscription, Long> {
    fun findByCustomerIdAndStatusIn(
        customerId: Long,
        statuses: List<SubscriptionStatus>,
    ): List<Subscription>

    @Query("SELECT s FROM Subscription s JOIN FETCH s.plan WHERE s.id = :id")
    fun findByIdWithPlan(
        @Param("id") id: Long,
    ): Subscription?

    fun findByCustomerId(customerId: Long): List<Subscription>
}
