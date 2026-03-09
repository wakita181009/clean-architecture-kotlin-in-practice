package com.wakita181009.classic.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SubscriptionStatusTest {
    // S-V1: Trial -> Active
    @Test
    fun `Trial can transition to Active`() {
        assertTrue(SubscriptionStatus.TRIAL.canTransitionTo(SubscriptionStatus.ACTIVE))
    }

    // S-V2: Trial -> Canceled
    @Test
    fun `Trial can transition to Canceled`() {
        assertTrue(SubscriptionStatus.TRIAL.canTransitionTo(SubscriptionStatus.CANCELED))
    }

    // S-V3: Trial -> Expired
    @Test
    fun `Trial can transition to Expired`() {
        assertTrue(SubscriptionStatus.TRIAL.canTransitionTo(SubscriptionStatus.EXPIRED))
    }

    // S-V4: Active -> Paused
    @Test
    fun `Active can transition to Paused`() {
        assertTrue(SubscriptionStatus.ACTIVE.canTransitionTo(SubscriptionStatus.PAUSED))
    }

    // S-V5: Active -> PastDue
    @Test
    fun `Active can transition to PastDue`() {
        assertTrue(SubscriptionStatus.ACTIVE.canTransitionTo(SubscriptionStatus.PAST_DUE))
    }

    // S-V6: Active -> Canceled
    @Test
    fun `Active can transition to Canceled`() {
        assertTrue(SubscriptionStatus.ACTIVE.canTransitionTo(SubscriptionStatus.CANCELED))
    }

    // S-V7: Paused -> Active
    @Test
    fun `Paused can transition to Active`() {
        assertTrue(SubscriptionStatus.PAUSED.canTransitionTo(SubscriptionStatus.ACTIVE))
    }

    // S-V8: Paused -> Canceled
    @Test
    fun `Paused can transition to Canceled`() {
        assertTrue(SubscriptionStatus.PAUSED.canTransitionTo(SubscriptionStatus.CANCELED))
    }

    // S-V10: PastDue -> Active
    @Test
    fun `PastDue can transition to Active`() {
        assertTrue(SubscriptionStatus.PAST_DUE.canTransitionTo(SubscriptionStatus.ACTIVE))
    }

    // S-V11: PastDue -> Canceled
    @Test
    fun `PastDue can transition to Canceled`() {
        assertTrue(SubscriptionStatus.PAST_DUE.canTransitionTo(SubscriptionStatus.CANCELED))
    }

    // S-I1: Trial cannot transition to Paused
    @Test
    fun `Trial cannot transition to Paused`() {
        assertFalse(SubscriptionStatus.TRIAL.canTransitionTo(SubscriptionStatus.PAUSED))
    }

    // S-I2: Trial cannot transition to PastDue
    @Test
    fun `Trial cannot transition to PastDue`() {
        assertFalse(SubscriptionStatus.TRIAL.canTransitionTo(SubscriptionStatus.PAST_DUE))
    }

    // S-I4: Active cannot transition to Trial
    @Test
    fun `Active cannot transition to Trial`() {
        assertFalse(SubscriptionStatus.ACTIVE.canTransitionTo(SubscriptionStatus.TRIAL))
    }

    // S-I5: Active cannot transition to Expired
    @Test
    fun `Active cannot transition to Expired`() {
        assertFalse(SubscriptionStatus.ACTIVE.canTransitionTo(SubscriptionStatus.EXPIRED))
    }

    // S-I6: Paused cannot transition to PastDue
    @Test
    fun `Paused cannot transition to PastDue`() {
        assertFalse(SubscriptionStatus.PAUSED.canTransitionTo(SubscriptionStatus.PAST_DUE))
    }

    // S-I7: Paused cannot transition to Trial
    @Test
    fun `Paused cannot transition to Trial`() {
        assertFalse(SubscriptionStatus.PAUSED.canTransitionTo(SubscriptionStatus.TRIAL))
    }

    // S-I8: Paused cannot transition to Expired
    @Test
    fun `Paused cannot transition to Expired`() {
        assertFalse(SubscriptionStatus.PAUSED.canTransitionTo(SubscriptionStatus.EXPIRED))
    }

    // S-I9: PastDue cannot transition to Paused
    @Test
    fun `PastDue cannot transition to Paused`() {
        assertFalse(SubscriptionStatus.PAST_DUE.canTransitionTo(SubscriptionStatus.PAUSED))
    }

    // S-I10: PastDue cannot transition to Trial
    @Test
    fun `PastDue cannot transition to Trial`() {
        assertFalse(SubscriptionStatus.PAST_DUE.canTransitionTo(SubscriptionStatus.TRIAL))
    }

    // S-I11: PastDue cannot transition to Expired
    @Test
    fun `PastDue cannot transition to Expired`() {
        assertFalse(SubscriptionStatus.PAST_DUE.canTransitionTo(SubscriptionStatus.EXPIRED))
    }

    // S-I12: Canceled is terminal
    @Test
    fun `Canceled cannot transition to any state`() {
        SubscriptionStatus.entries.forEach { target ->
            assertFalse(SubscriptionStatus.CANCELED.canTransitionTo(target))
        }
    }

    // S-I13: Expired is terminal
    @Test
    fun `Expired cannot transition to any state`() {
        SubscriptionStatus.entries.forEach { target ->
            assertFalse(SubscriptionStatus.EXPIRED.canTransitionTo(target))
        }
    }
}
