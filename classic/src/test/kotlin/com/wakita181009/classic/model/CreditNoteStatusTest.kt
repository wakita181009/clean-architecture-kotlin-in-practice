package com.wakita181009.classic.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CreditNoteStatusTest {
    // CN-S1: Issued -> Applied
    @Test
    fun `ISSUED can transition to APPLIED`() {
        assertTrue(CreditNoteStatus.ISSUED.canTransitionTo(CreditNoteStatus.APPLIED))
    }

    // CN-S3: Issued -> Voided
    @Test
    fun `ISSUED can transition to VOIDED`() {
        assertTrue(CreditNoteStatus.ISSUED.canTransitionTo(CreditNoteStatus.VOIDED))
    }

    // CN-I1: Applied -> any state
    @Test
    fun `APPLIED cannot transition to any state`() {
        assertFalse(CreditNoteStatus.APPLIED.canTransitionTo(CreditNoteStatus.ISSUED))
        assertFalse(CreditNoteStatus.APPLIED.canTransitionTo(CreditNoteStatus.VOIDED))
        assertFalse(CreditNoteStatus.APPLIED.canTransitionTo(CreditNoteStatus.APPLIED))
    }

    // CN-I2: Voided -> any state
    @Test
    fun `VOIDED cannot transition to any state`() {
        assertFalse(CreditNoteStatus.VOIDED.canTransitionTo(CreditNoteStatus.ISSUED))
        assertFalse(CreditNoteStatus.VOIDED.canTransitionTo(CreditNoteStatus.APPLIED))
        assertFalse(CreditNoteStatus.VOIDED.canTransitionTo(CreditNoteStatus.VOIDED))
    }

    // CN-I3: Issued -> Issued (no self-transition)
    @Test
    fun `ISSUED cannot self-transition`() {
        assertFalse(CreditNoteStatus.ISSUED.canTransitionTo(CreditNoteStatus.ISSUED))
    }
}
