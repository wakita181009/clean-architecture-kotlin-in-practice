package com.wakita181009.clean.infrastructure.command.repository

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.wakita181009.clean.application.command.port.InvoiceCommandQueryPort
import com.wakita181009.clean.domain.error.DomainError
import com.wakita181009.clean.domain.model.Currency
import com.wakita181009.clean.domain.model.Invoice
import com.wakita181009.clean.domain.model.InvoiceId
import com.wakita181009.clean.domain.model.InvoiceLineItem
import com.wakita181009.clean.domain.model.InvoiceLineItemType
import com.wakita181009.clean.domain.model.InvoiceStatus
import com.wakita181009.clean.domain.model.Money
import com.wakita181009.clean.domain.model.SubscriptionId
import com.wakita181009.clean.domain.repository.InvoiceRepository
import org.jooq.DSLContext
import org.jooq.generated.tables.references.INVOICES
import org.jooq.generated.tables.references.INVOICE_LINE_ITEMS
import org.springframework.stereotype.Repository
import java.time.ZoneOffset

@Repository
class InvoiceRepositoryImpl(
    private val dsl: DSLContext,
) : InvoiceRepository, InvoiceCommandQueryPort {

    override fun save(invoice: Invoice): Either<DomainError, Invoice> = Either.catch {
        if (invoice.id == null) {
            val record = dsl.insertInto(INVOICES)
                .set(INVOICES.SUBSCRIPTION_ID, invoice.subscriptionId.value)
                .set(INVOICES.SUBTOTAL_AMOUNT, invoice.subtotal.amount)
                .set(INVOICES.DISCOUNT_AMOUNT, invoice.discountAmount.amount)
                .set(INVOICES.TOTAL_AMOUNT, invoice.total.amount)
                .set(INVOICES.CURRENCY, invoice.currency.name)
                .set(INVOICES.STATUS, toDbInvoiceStatus(invoice.status))
                .set(INVOICES.DUE_DATE, invoice.dueDate)
                .set(INVOICES.PAID_AT, invoice.paidAt?.atOffset(ZoneOffset.UTC))
                .set(INVOICES.PAYMENT_ATTEMPT_COUNT, invoice.paymentAttemptCount)
                .set(INVOICES.CREATED_AT, invoice.createdAt.atOffset(ZoneOffset.UTC))
                .set(INVOICES.UPDATED_AT, invoice.updatedAt.atOffset(ZoneOffset.UTC))
                .returningResult(INVOICES.ID)
                .fetchOne()!!

            val invoiceId = record.get(INVOICES.ID)!!

            // Save line items
            for (item in invoice.lineItems) {
                dsl.insertInto(INVOICE_LINE_ITEMS)
                    .set(INVOICE_LINE_ITEMS.INVOICE_ID, invoiceId)
                    .set(INVOICE_LINE_ITEMS.DESCRIPTION, item.description)
                    .set(INVOICE_LINE_ITEMS.AMOUNT, item.amount.amount)
                    .set(INVOICE_LINE_ITEMS.CURRENCY, item.amount.currency.name)
                    .set(INVOICE_LINE_ITEMS.TYPE, item.type.name)
                    .execute()
            }

            invoice.copy(id = InvoiceId(invoiceId).getOrNull()!!)
        } else {
            dsl.update(INVOICES)
                .set(INVOICES.STATUS, toDbInvoiceStatus(invoice.status))
                .set(INVOICES.PAID_AT, invoice.paidAt?.atOffset(ZoneOffset.UTC))
                .set(INVOICES.PAYMENT_ATTEMPT_COUNT, invoice.paymentAttemptCount)
                .set(INVOICES.UPDATED_AT, invoice.updatedAt.atOffset(ZoneOffset.UTC))
                .where(INVOICES.ID.eq(invoice.id!!.value))
                .execute()
            invoice
        }
    }.mapLeft { InfraError.DatabaseError(it.message ?: "Unknown DB error") }

    override fun findById(id: InvoiceId): Either<DomainError, Invoice> = either {
        val record = Either.catch {
            dsl.selectFrom(INVOICES)
                .where(INVOICES.ID.eq(id.value))
                .fetchOne()
        }.mapLeft { InfraError.DatabaseError(it.message ?: "Unknown") }.bind()

        ensureNotNull(record) { InfraError.NotFound("Invoice", id.value) }
        mapToInvoice(record).bind()
    }

    override fun findOpenBySubscriptionId(subscriptionId: SubscriptionId): Either<DomainError, List<Invoice>> = either {
        val records = Either.catch {
            dsl.selectFrom(INVOICES)
                .where(INVOICES.SUBSCRIPTION_ID.eq(subscriptionId.value))
                .and(INVOICES.STATUS.eq("OPEN"))
                .fetch()
        }.mapLeft { InfraError.DatabaseError(it.message ?: "Unknown") }.bind()

        records.map { mapToInvoice(it).bind() }
    }

    private fun mapToInvoice(record: org.jooq.generated.tables.records.InvoicesRecord): Either<DomainError, Invoice> = either {
        val invoiceId = InvoiceId(record.id!!).mapLeft { it as DomainError }.bind()
        val subscriptionId = SubscriptionId(record.subscriptionId!!).mapLeft { it as DomainError }.bind()
        val currency = Currency.valueOf(record.currency!!)

        val lineItemRecords = Either.catch {
            dsl.selectFrom(INVOICE_LINE_ITEMS)
                .where(INVOICE_LINE_ITEMS.INVOICE_ID.eq(record.id!!))
                .fetch()
        }.mapLeft { InfraError.DatabaseError(it.message ?: "Unknown") }.bind()

        val lineItems = lineItemRecords.map { li ->
            val liCurrency = Currency.valueOf(li.currency!!)
            val liMoney = Money.of(li.amount!!, liCurrency).bind()
            InvoiceLineItem(
                description = li.description!!,
                amount = liMoney,
                type = InvoiceLineItemType.valueOf(li.type!!),
            )
        }

        val subtotal = Money.of(record.subtotalAmount!!, currency).bind()
        val discountAmount = Money.of(record.discountAmount!!, currency).bind()
        val total = Money.of(record.totalAmount!!, currency).bind()
        val status = toDomainInvoiceStatus(record.status!!, record.paidAt?.toInstant()).bind()

        Invoice(
            id = invoiceId,
            subscriptionId = subscriptionId,
            lineItems = lineItems,
            subtotal = subtotal,
            discountAmount = discountAmount,
            total = total,
            currency = currency,
            status = status,
            dueDate = record.dueDate!!,
            paidAt = record.paidAt?.toInstant(),
            paymentAttemptCount = record.paymentAttemptCount!!,
            createdAt = record.createdAt!!.toInstant(),
            updatedAt = record.updatedAt!!.toInstant(),
        )
    }

    companion object {
        fun toDbInvoiceStatus(status: InvoiceStatus): String = when (status) {
            is InvoiceStatus.Draft -> "DRAFT"
            is InvoiceStatus.Open -> "OPEN"
            is InvoiceStatus.Paid -> "PAID"
            is InvoiceStatus.Void -> "VOID"
            is InvoiceStatus.Uncollectible -> "UNCOLLECTIBLE"
        }

        fun toDomainInvoiceStatus(dbStatus: String, paidAt: java.time.Instant?): Either<DomainError, InvoiceStatus> =
            when (dbStatus) {
                "DRAFT" -> Either.Right(InvoiceStatus.Draft)
                "OPEN" -> Either.Right(InvoiceStatus.Open)
                "PAID" -> Either.Right(InvoiceStatus.Paid(paidAt ?: java.time.Instant.EPOCH))
                "VOID" -> Either.Right(InvoiceStatus.Void)
                "UNCOLLECTIBLE" -> Either.Right(InvoiceStatus.Uncollectible)
                else -> Either.Left(InfraError.UnknownValue("InvoiceStatus", dbStatus))
            }
    }
}
