package com.wakita181009.classic.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InvoiceStatusTest {
    // IS-V1: Draft -> Open
    @Test
    fun `Draft can transition to Open`() {
        assertTrue(InvoiceStatus.DRAFT.canTransitionTo(InvoiceStatus.OPEN))
    }

    // IS-V2: Draft -> Void
    @Test
    fun `Draft can transition to Void`() {
        assertTrue(InvoiceStatus.DRAFT.canTransitionTo(InvoiceStatus.VOID))
    }

    // IS-V3: Open -> Paid
    @Test
    fun `Open can transition to Paid`() {
        assertTrue(InvoiceStatus.OPEN.canTransitionTo(InvoiceStatus.PAID))
    }

    // IS-V4: Open -> Void
    @Test
    fun `Open can transition to Void`() {
        assertTrue(InvoiceStatus.OPEN.canTransitionTo(InvoiceStatus.VOID))
    }

    // IS-V5: Open -> Uncollectible
    @Test
    fun `Open can transition to Uncollectible`() {
        assertTrue(InvoiceStatus.OPEN.canTransitionTo(InvoiceStatus.UNCOLLECTIBLE))
    }

    // IS-I1: Open cannot revert to Draft
    @Test
    fun `Open cannot transition to Draft`() {
        assertFalse(InvoiceStatus.OPEN.canTransitionTo(InvoiceStatus.DRAFT))
    }

    // IS-I2: Paid is terminal
    @Test
    fun `Paid cannot transition to any state`() {
        InvoiceStatus.entries.forEach { target ->
            assertFalse(InvoiceStatus.PAID.canTransitionTo(target))
        }
    }

    // IS-I3: Void is terminal
    @Test
    fun `Void cannot transition to any state`() {
        InvoiceStatus.entries.forEach { target ->
            assertFalse(InvoiceStatus.VOID.canTransitionTo(target))
        }
    }

    // IS-I4: Uncollectible is terminal
    @Test
    fun `Uncollectible cannot transition to any state`() {
        InvoiceStatus.entries.forEach { target ->
            assertFalse(InvoiceStatus.UNCOLLECTIBLE.canTransitionTo(target))
        }
    }

    // IS-I5: Draft cannot go directly to Paid
    @Test
    fun `Draft cannot transition to Paid`() {
        assertFalse(InvoiceStatus.DRAFT.canTransitionTo(InvoiceStatus.PAID))
    }

    // IS-I6: Draft cannot go directly to Uncollectible
    @Test
    fun `Draft cannot transition to Uncollectible`() {
        assertFalse(InvoiceStatus.DRAFT.canTransitionTo(InvoiceStatus.UNCOLLECTIBLE))
    }
}
