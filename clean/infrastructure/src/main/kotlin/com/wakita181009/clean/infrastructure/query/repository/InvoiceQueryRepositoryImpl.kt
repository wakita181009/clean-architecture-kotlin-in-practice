package com.wakita181009.clean.infrastructure.query.repository

import arrow.core.Either
import arrow.core.raise.either
import com.wakita181009.clean.application.query.dto.InvoiceDto
import com.wakita181009.clean.application.query.dto.InvoiceLineItemDto
import com.wakita181009.clean.application.query.error.InvoiceListBySubscriptionQueryError
import com.wakita181009.clean.application.query.repository.InvoiceQueryRepository
import org.jooq.DSLContext
import org.jooq.generated.tables.references.INVOICES
import org.jooq.generated.tables.references.INVOICE_LINE_ITEMS
import org.springframework.stereotype.Repository

@Repository
class InvoiceQueryRepositoryImpl(
    private val dsl: DSLContext,
) : InvoiceQueryRepository {

    override fun listBySubscriptionId(subscriptionId: Long): Either<InvoiceListBySubscriptionQueryError, List<InvoiceDto>> = either {
        val invoiceRecords = Either.catch {
            dsl.selectFrom(INVOICES)
                .where(INVOICES.SUBSCRIPTION_ID.eq(subscriptionId))
                .orderBy(INVOICES.CREATED_AT.desc())
                .fetch()
        }.mapLeft { InvoiceListBySubscriptionQueryError.Internal(it.message ?: "Unknown") }.bind()

        invoiceRecords.map { record ->
            val lineItemRecords = Either.catch {
                dsl.selectFrom(INVOICE_LINE_ITEMS)
                    .where(INVOICE_LINE_ITEMS.INVOICE_ID.eq(record.id!!))
                    .fetch()
            }.mapLeft { InvoiceListBySubscriptionQueryError.Internal(it.message ?: "Unknown") }.bind()

            val lineItems = lineItemRecords.map { li ->
                InvoiceLineItemDto(
                    description = li.description!!,
                    amount = li.amount!!.toPlainString(),
                    currency = li.currency!!,
                    type = li.type!!,
                )
            }

            InvoiceDto(
                id = record.id!!,
                subscriptionId = record.subscriptionId!!,
                lineItems = lineItems,
                subtotalAmount = record.subtotalAmount!!.toPlainString(),
                subtotalCurrency = record.currency!!,
                discountAmount = record.discountAmount!!.toPlainString(),
                totalAmount = record.totalAmount!!.toPlainString(),
                totalCurrency = record.currency!!,
                status = record.status!!,
                dueDate = record.dueDate!!,
                paidAt = record.paidAt?.toInstant(),
                paymentAttemptCount = record.paymentAttemptCount!!,
                createdAt = record.createdAt!!.toInstant(),
            )
        }
    }
}
