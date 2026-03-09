package com.wakita181009.classic.repository

import com.wakita181009.classic.model.CreditNote
import com.wakita181009.classic.model.CreditNoteStatus
import org.springframework.data.jpa.repository.JpaRepository

interface CreditNoteRepository : JpaRepository<CreditNote, Long> {
    fun findByInvoiceId(invoiceId: Long): List<CreditNote>

    fun findByInvoiceIdAndStatusIn(
        invoiceId: Long,
        statuses: List<CreditNoteStatus>,
    ): List<CreditNote>
}
