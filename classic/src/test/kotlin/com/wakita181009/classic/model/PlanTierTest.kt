package com.wakita181009.classic.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlanTierTest {
    // V-T1: Upgrade
    @Test
    fun `STARTER to PROFESSIONAL is upgrade`() {
        assertTrue(PlanTier.STARTER.isUpgradeTo(PlanTier.PROFESSIONAL))
    }

    // V-T2: Downgrade
    @Test
    fun `PROFESSIONAL to STARTER is not upgrade`() {
        assertFalse(PlanTier.PROFESSIONAL.isUpgradeTo(PlanTier.STARTER))
    }

    // V-T3: Same tier
    @Test
    fun `same tier is not upgrade`() {
        assertFalse(PlanTier.STARTER.isUpgradeTo(PlanTier.STARTER))
    }

    // V-T4: FREE to any is upgrade
    @Test
    fun `FREE to STARTER is upgrade`() {
        assertTrue(PlanTier.FREE.isUpgradeTo(PlanTier.STARTER))
    }

    // V-T5: Any to FREE is not upgrade
    @Test
    fun `ENTERPRISE to FREE is not upgrade`() {
        assertFalse(PlanTier.ENTERPRISE.isUpgradeTo(PlanTier.FREE))
    }
}
