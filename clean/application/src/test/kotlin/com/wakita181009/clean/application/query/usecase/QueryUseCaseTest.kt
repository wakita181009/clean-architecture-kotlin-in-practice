package com.wakita181009.clean.application.query.usecase

import arrow.core.right
import com.wakita181009.clean.application.query.dto.SubscriptionDto
import com.wakita181009.clean.application.query.error.SubscriptionFindByIdQueryError
import com.wakita181009.clean.application.query.error.SubscriptionListByCustomerQueryError
import com.wakita181009.clean.application.query.repository.SubscriptionQueryRepository
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import java.time.Instant

class QueryUseCaseTest : DescribeSpec({

    val subscriptionQueryRepository = mockk<SubscriptionQueryRepository>()
    val findByIdUseCase = SubscriptionFindByIdQueryUseCaseImpl(subscriptionQueryRepository)
    val listByCustomerUseCase = SubscriptionListByCustomerQueryUseCaseImpl(subscriptionQueryRepository)

    val now = Instant.parse("2025-01-15T00:00:00Z")

    fun sampleDto() = SubscriptionDto(
        id = 1L,
        customerId = 1L,
        planId = 1L,
        planName = "Pro",
        planTier = "PROFESSIONAL",
        planBillingInterval = "MONTHLY",
        planBasePriceAmount = "49.99",
        planBasePriceCurrency = "USD",
        status = "ACTIVE",
        currentPeriodStart = now,
        currentPeriodEnd = now,
        trialEnd = null,
        pausedAt = null,
        canceledAt = null,
        cancelAtPeriodEnd = false,
        discountType = null,
        discountValue = null,
        discountRemainingCycles = null,
        createdAt = now,
        updatedAt = now,
    )

    beforeTest {
        clearMocks(subscriptionQueryRepository)
    }

    describe("SubscriptionFindByIdQueryUseCase") {
        it("returns subscription dto") {
            every { subscriptionQueryRepository.findById(1L) } returns sampleDto().right()
            val result = findByIdUseCase.execute(1L).shouldBeRight()
            result.id shouldBe 1L
            result.planName shouldBe "Pro"
        }

        it("rejects invalid ID (zero)") {
            findByIdUseCase.execute(0L).shouldBeLeft()
                .shouldBeInstanceOf<SubscriptionFindByIdQueryError.InvalidInput>()
        }

        it("rejects negative ID") {
            findByIdUseCase.execute(-1L).shouldBeLeft()
                .shouldBeInstanceOf<SubscriptionFindByIdQueryError.InvalidInput>()
        }
    }

    describe("SubscriptionListByCustomerQueryUseCase") {
        it("returns list of subscriptions") {
            every { subscriptionQueryRepository.listByCustomerId(1L) } returns listOf(sampleDto()).right()
            val result = listByCustomerUseCase.execute(1L).shouldBeRight()
            result.size shouldBe 1
        }

        it("rejects invalid customer ID") {
            listByCustomerUseCase.execute(0L).shouldBeLeft()
                .shouldBeInstanceOf<SubscriptionListByCustomerQueryError.InvalidInput>()
        }
    }
})
