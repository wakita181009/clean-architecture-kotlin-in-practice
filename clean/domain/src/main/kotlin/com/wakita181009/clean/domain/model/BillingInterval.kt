package com.wakita181009.clean.domain.model

import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

enum class BillingInterval {
    MONTHLY,
    YEARLY,
    ;

    fun nextPeriodEnd(start: Instant): Instant {
        val zdt = ZonedDateTime.ofInstant(start, ZoneOffset.UTC)
        return when (this) {
            MONTHLY -> zdt.plusMonths(1)
            YEARLY -> zdt.plusYears(1)
        }.toInstant()
    }
}
