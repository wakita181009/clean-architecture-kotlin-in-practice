package com.wakita181009.classic.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class BillingIntervalTest {
    // V-BI1: Monthly period calculation
    @Test
    fun `MONTHLY adds one month`() {
        val start = Instant.parse("2025-01-15T00:00:00Z")
        val end = BillingInterval.MONTHLY.addTo(start)
        assertEquals(Instant.parse("2025-02-15T00:00:00Z"), end)
    }

    // V-BI2: Yearly period calculation
    @Test
    fun `YEARLY adds one year`() {
        val start = Instant.parse("2025-01-15T00:00:00Z")
        val end = BillingInterval.YEARLY.addTo(start)
        assertEquals(Instant.parse("2026-01-15T00:00:00Z"), end)
    }

    // V-BI3: Monthly end-of-month edge case
    @Test
    fun `MONTHLY from Jan 31 goes to Feb 28`() {
        val start = Instant.parse("2025-01-31T00:00:00Z")
        val end = BillingInterval.MONTHLY.addTo(start)
        assertEquals(Instant.parse("2025-02-28T00:00:00Z"), end)
    }

    // V-BI4: Monthly leap year
    @Test
    fun `MONTHLY from Jan 31 in leap year goes to Feb 29`() {
        val start = Instant.parse("2024-01-31T00:00:00Z")
        val end = BillingInterval.MONTHLY.addTo(start)
        assertEquals(Instant.parse("2024-02-29T00:00:00Z"), end)
    }

    // V-BI5: Yearly from leap day
    @Test
    fun `YEARLY from Feb 29 leap year goes to Feb 28 next year`() {
        val start = Instant.parse("2024-02-29T00:00:00Z")
        val end = BillingInterval.YEARLY.addTo(start)
        assertEquals(Instant.parse("2025-02-28T00:00:00Z"), end)
    }
}
