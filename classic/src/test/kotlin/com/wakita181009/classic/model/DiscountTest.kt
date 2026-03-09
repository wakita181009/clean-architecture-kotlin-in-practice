package com.wakita181009.classic.model

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class DiscountTest {
    private val now = Instant.parse("2025-01-15T00:00:00Z")

    // V-D1: Valid percentage
    @Test
    fun `valid percentage discount`() {
        assertDoesNotThrow {
            Discount(
                type = DiscountType.PERCENTAGE,
                value = BigDecimal("20"),
                durationMonths = 3,
                remainingCycles = 3,
                appliedAt = now,
            )
        }
    }

    // V-D2: Percentage at minimum
    @Test
    fun `percentage at minimum value 1`() {
        assertDoesNotThrow {
            Discount(
                type = DiscountType.PERCENTAGE,
                value = BigDecimal("1"),
                durationMonths = 1,
                remainingCycles = 1,
                appliedAt = now,
            )
        }
    }

    // V-D3: Percentage at maximum
    @Test
    fun `percentage at maximum value 100`() {
        assertDoesNotThrow {
            Discount(
                type = DiscountType.PERCENTAGE,
                value = BigDecimal("100"),
                durationMonths = 1,
                remainingCycles = 1,
                appliedAt = now,
            )
        }
    }

    // V-D4: Percentage below minimum
    @Test
    fun `percentage below minimum throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            Discount(
                type = DiscountType.PERCENTAGE,
                value = BigDecimal("0"),
                durationMonths = 1,
                remainingCycles = 1,
                appliedAt = now,
            )
        }
    }

    // V-D5: Percentage above maximum
    @Test
    fun `percentage above maximum throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            Discount(
                type = DiscountType.PERCENTAGE,
                value = BigDecimal("101"),
                durationMonths = 1,
                remainingCycles = 1,
                appliedAt = now,
            )
        }
    }

    // V-D6: Valid fixed amount
    @Test
    fun `valid fixed amount discount`() {
        assertDoesNotThrow {
            Discount(
                type = DiscountType.FIXED_AMOUNT,
                value = BigDecimal("10.00"),
                durationMonths = 6,
                remainingCycles = 6,
                appliedAt = now,
            )
        }
    }

    // V-D7: Fixed amount zero throws
    @Test
    fun `fixed amount zero throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            Discount(
                type = DiscountType.FIXED_AMOUNT,
                value = BigDecimal("0.00"),
                durationMonths = 1,
                remainingCycles = 1,
                appliedAt = now,
            )
        }
    }

    // V-D8: Duration at minimum
    @Test
    fun `duration at minimum 1 is valid`() {
        assertDoesNotThrow {
            Discount(
                type = DiscountType.PERCENTAGE,
                value = BigDecimal("10"),
                durationMonths = 1,
                remainingCycles = 1,
                appliedAt = now,
            )
        }
    }

    // V-D9: Duration at maximum
    @Test
    fun `duration at maximum 24 is valid`() {
        assertDoesNotThrow {
            Discount(
                type = DiscountType.PERCENTAGE,
                value = BigDecimal("10"),
                durationMonths = 24,
                remainingCycles = 24,
                appliedAt = now,
            )
        }
    }

    // V-D10: Duration null (forever)
    @Test
    fun `duration null means forever`() {
        assertDoesNotThrow {
            Discount(
                type = DiscountType.PERCENTAGE,
                value = BigDecimal("10"),
                durationMonths = null,
                remainingCycles = null,
                appliedAt = now,
            )
        }
    }

    // V-D11: Duration above maximum
    @Test
    fun `duration above maximum 25 throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            Discount(
                type = DiscountType.PERCENTAGE,
                value = BigDecimal("10"),
                durationMonths = 25,
                remainingCycles = 25,
                appliedAt = now,
            )
        }
    }
}
