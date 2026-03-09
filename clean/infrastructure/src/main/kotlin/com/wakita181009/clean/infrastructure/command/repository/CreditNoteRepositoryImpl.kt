package com.wakita181009.clean.infrastructure.command.repository

import arrow.core.Either
import arrow.core.raise.either
import com.wakita181009.clean.application.command.port.CreditNoteCommandQueryPort
import com.wakita181009.clean.domain.error.DomainError
import com.wakita181009.clean.domain.model.CreditNote
import com.wakita181009.clean.domain.model.CreditNoteApplication
import com.wakita181009.clean.domain.model.CreditNoteId
import com.wakita181009.clean.domain.model.CreditNoteStatus
import com.wakita181009.clean.domain.model.CreditNoteType
import com.wakita181009.clean.domain.model.Currency
import com.wakita181009.clean.domain.model.InvoiceId
import com.wakita181009.clean.domain.model.Money
import com.wakita181009.clean.domain.model.SubscriptionId
import com.wakita181009.clean.domain.repository.CreditNoteRepository
import org.jooq.DSLContext
import org.jooq.generated.tables.records.CreditNotesRecord
import org.jooq.generated.tables.references.CREDIT_NOTES
import org.springframework.stereotype.Repository
import java.time.ZoneOffset

@Repository
class CreditNoteRepositoryImpl(
    private val dsl: DSLContext,
) : CreditNoteRepository, CreditNoteCommandQueryPort {

    override fun save(creditNote: CreditNote): Either<DomainError, CreditNote> = Either.catch {
        if (creditNote.id == null) {
            val record = dsl.insertInto(CREDIT_NOTES)
                .set(CREDIT_NOTES.INVOICE_ID, creditNote.invoiceId.value)
                .set(CREDIT_NOTES.SUBSCRIPTION_ID, creditNote.subscriptionId.value)
                .set(CREDIT_NOTES.AMOUNT, creditNote.amount.amount)
                .set(CREDIT_NOTES.CURRENCY, creditNote.amount.currency.name)
                .set(CREDIT_NOTES.REASON, creditNote.reason)
                .set(CREDIT_NOTES.TYPE, creditNote.type.name)
                .set(CREDIT_NOTES.APPLICATION, creditNote.application.name)
                .set(CREDIT_NOTES.STATUS, toDbStatus(creditNote.status))
                .set(CREDIT_NOTES.REFUND_TRANSACTION_ID, creditNote.refundTransactionId)
                .set(CREDIT_NOTES.CREATED_AT, creditNote.createdAt.atOffset(ZoneOffset.UTC))
                .set(CREDIT_NOTES.UPDATED_AT, creditNote.updatedAt.atOffset(ZoneOffset.UTC))
                .returningResult(CREDIT_NOTES.ID)
                .fetchOne()!!

            creditNote.copy(id = CreditNoteId(record.get(CREDIT_NOTES.ID)!!).getOrNull()!!)
        } else {
            dsl.update(CREDIT_NOTES)
                .set(CREDIT_NOTES.STATUS, toDbStatus(creditNote.status))
                .set(CREDIT_NOTES.REFUND_TRANSACTION_ID, creditNote.refundTransactionId)
                .set(CREDIT_NOTES.UPDATED_AT, creditNote.updatedAt.atOffset(ZoneOffset.UTC))
                .where(CREDIT_NOTES.ID.eq(creditNote.id!!.value))
                .execute()
            creditNote
        }
    }.mapLeft { InfraError.DatabaseError(it.message ?: "Unknown DB error") }

    override fun findByInvoiceId(invoiceId: InvoiceId): Either<DomainError, List<CreditNote>> = either {
        val records = Either.catch {
            dsl.selectFrom(CREDIT_NOTES)
                .where(CREDIT_NOTES.INVOICE_ID.eq(invoiceId.value))
                .fetch()
        }.mapLeft { InfraError.DatabaseError(it.message ?: "Unknown") }.bind()

        records.map { mapToCreditNote(it).bind() }
    }

    private fun mapToCreditNote(record: CreditNotesRecord): Either<DomainError, CreditNote> = either {
        val id = CreditNoteId(record.id!!).mapLeft { it as DomainError }.bind()
        val invoiceId = InvoiceId(record.invoiceId!!).mapLeft { it as DomainError }.bind()
        val subscriptionId = SubscriptionId(record.subscriptionId!!).mapLeft { it as DomainError }.bind()
        val currency = Currency.valueOf(record.currency!!)
        val amount = Money.of(record.amount!!, currency).bind()

        val type = Either.catch { CreditNoteType.valueOf(record.type!!) }
            .mapLeft { InfraError.UnknownValue("CreditNoteType", record.type!!) as DomainError }
            .bind()

        val application = Either.catch { CreditNoteApplication.valueOf(record.application!!) }
            .mapLeft { InfraError.UnknownValue("CreditNoteApplication", record.application!!) as DomainError }
            .bind()

        val status = toDomainStatus(record.status!!).bind()

        CreditNote(
            id = id,
            invoiceId = invoiceId,
            subscriptionId = subscriptionId,
            amount = amount,
            reason = record.reason!!,
            type = type,
            application = application,
            status = status,
            refundTransactionId = record.refundTransactionId,
            createdAt = record.createdAt!!.toInstant(),
            updatedAt = record.updatedAt!!.toInstant(),
        )
    }

    companion object {
        fun toDbStatus(status: CreditNoteStatus): String = when (status) {
            is CreditNoteStatus.Issued -> "ISSUED"
            is CreditNoteStatus.Applied -> "APPLIED"
            is CreditNoteStatus.Voided -> "VOIDED"
        }

        fun toDomainStatus(dbStatus: String): Either<DomainError, CreditNoteStatus> = when (dbStatus) {
            "ISSUED" -> Either.Right(CreditNoteStatus.Issued)
            "APPLIED" -> Either.Right(CreditNoteStatus.Applied)
            "VOIDED" -> Either.Right(CreditNoteStatus.Voided)
            else -> Either.Left(InfraError.UnknownValue("CreditNoteStatus", dbStatus))
        }
    }
}
