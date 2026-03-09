package com.wakita181009.classic.repository

import com.wakita181009.classic.model.Invoice
import com.wakita181009.classic.model.InvoiceStatus
import org.springframework.data.jpa.repository.JpaRepository

interface InvoiceRepository : JpaRepository<Invoice, Long> {
    fun findBySubscriptionId(subscriptionId: Long): List<Invoice>

    fun findBySubscriptionIdAndStatus(
        subscriptionId: Long,
        status: InvoiceStatus,
    ): List<Invoice>
}
