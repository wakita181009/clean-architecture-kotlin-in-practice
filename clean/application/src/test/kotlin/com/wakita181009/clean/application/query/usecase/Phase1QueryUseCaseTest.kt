package com.wakita181009.clean.application.query.usecase

import arrow.core.right
import com.wakita181009.clean.application.query.dto.CreditNoteDto
import com.wakita181009.clean.application.query.dto.SubscriptionAddOnDto
import com.wakita181009.clean.application.query.error.CreditNoteListQueryError
import com.wakita181009.clean.application.query.error.SubscriptionAddOnListQueryError
import com.wakita181009.clean.application.query.repository.CreditNoteQueryRepository
import com.wakita181009.clean.application.query.repository.SubscriptionAddOnQueryRepository
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import java.time.Instant

class Phase1QueryUseCaseTest : DescribeSpec({

    val subscriptionAddOnQueryRepository = mockk<SubscriptionAddOnQueryRepository>()
    val creditNoteQueryRepository = mockk<CreditNoteQueryRepository>()

    val addOnListUseCase = SubscriptionAddOnListQueryUseCaseImpl(subscriptionAddOnQueryRepository)
    val creditNoteListUseCase = CreditNoteListQueryUseCaseImpl(creditNoteQueryRepository)

    val now = Instant.parse("2025-01-15T00:00:00Z")

    beforeTest {
        clearMocks(subscriptionAddOnQueryRepository, creditNoteQueryRepository)
    }

    describe("SubscriptionAddOnListQueryUseCase") {
        // API-LA1
        it("lists subscription add-ons") {
            val dto = SubscriptionAddOnDto(
                id = 1L, subscriptionId = 1L, addOnId = 1L, addOnName = "Support",
                addOnPriceAmount = "9.99", addOnPriceCurrency = "USD", addOnBillingType = "FLAT",
                quantity = 1, status = "ACTIVE", attachedAt = now, detachedAt = null,
            )
            every { subscriptionAddOnQueryRepository.findBySubscriptionId(1L) } returns listOf(dto).right()

            val result = addOnListUseCase.execute(1L).shouldBeRight()
            result.size shouldBe 1
            result[0].addOnName shouldBe "Support"
        }

        // API-LA2
        it("returns empty list when no add-ons") {
            every { subscriptionAddOnQueryRepository.findBySubscriptionId(1L) } returns emptyList<SubscriptionAddOnDto>().right()

            val result = addOnListUseCase.execute(1L).shouldBeRight()
            result.size shouldBe 0
        }

        // API-LA3
        it("returns error for invalid subscription ID") {
            addOnListUseCase.execute(-1L).shouldBeLeft().shouldBeInstanceOf<SubscriptionAddOnListQueryError.InvalidInput>()
        }
    }

    describe("CreditNoteListQueryUseCase") {
        // API-LC1
        it("lists credit notes for invoice") {
            val dto = CreditNoteDto(
                id = 1L, invoiceId = 1L, subscriptionId = 1L,
                amount = "49.99", currency = "USD", reason = "Refund",
                type = "FULL", application = "REFUND_TO_PAYMENT", status = "APPLIED",
                refundTransactionId = "ref_1", createdAt = now, updatedAt = now,
            )
            every { creditNoteQueryRepository.findByInvoiceId(1L) } returns listOf(dto).right()

            val result = creditNoteListUseCase.execute(1L).shouldBeRight()
            result.size shouldBe 1
            result[0].type shouldBe "FULL"
        }

        // API-LC2
        it("returns empty list when no credit notes") {
            every { creditNoteQueryRepository.findByInvoiceId(1L) } returns emptyList<CreditNoteDto>().right()

            val result = creditNoteListUseCase.execute(1L).shouldBeRight()
            result.size shouldBe 0
        }

        // API-LC3
        it("returns error for invalid invoice ID") {
            creditNoteListUseCase.execute(-1L).shouldBeLeft().shouldBeInstanceOf<CreditNoteListQueryError.InvalidInput>()
        }
    }
})
