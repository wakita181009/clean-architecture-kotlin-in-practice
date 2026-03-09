package com.wakita181009.clean.infrastructure.query.repository

import arrow.core.Either
import com.wakita181009.clean.application.query.dto.CreditNoteDto
import com.wakita181009.clean.application.query.error.CreditNoteListQueryError
import com.wakita181009.clean.application.query.repository.CreditNoteQueryRepository
import org.jooq.DSLContext
import org.jooq.generated.tables.references.CREDIT_NOTES
import org.springframework.stereotype.Repository

@Repository
class CreditNoteQueryRepositoryImpl(
    private val dsl: DSLContext,
) : CreditNoteQueryRepository {

    override fun findByInvoiceId(invoiceId: Long): Either<CreditNoteListQueryError, List<CreditNoteDto>> =
        Either.catch {
            dsl.selectFrom(CREDIT_NOTES)
                .where(CREDIT_NOTES.INVOICE_ID.eq(invoiceId))
                .fetch()
                .map { record ->
                    CreditNoteDto(
                        id = record.id!!,
                        invoiceId = record.invoiceId!!,
                        subscriptionId = record.subscriptionId!!,
                        amount = record.amount!!.toPlainString(),
                        currency = record.currency!!,
                        reason = record.reason!!,
                        type = record.type!!,
                        application = record.application!!,
                        status = record.status!!,
                        refundTransactionId = record.refundTransactionId,
                        createdAt = record.createdAt!!.toInstant(),
                        updatedAt = record.updatedAt!!.toInstant(),
                    )
                }
        }.mapLeft { CreditNoteListQueryError.Internal(it.message ?: "Unknown error") }
}
