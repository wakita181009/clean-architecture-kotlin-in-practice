package com.wakita181009.classic.repository

import com.wakita181009.classic.model.Discount
import org.springframework.data.jpa.repository.JpaRepository

interface DiscountRepository : JpaRepository<Discount, Long> {
    fun findBySubscriptionId(subscriptionId: Long): Discount?
}
