package com.wakita181009.clean.domain.model

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ValueObjectTest : DescribeSpec({

    describe("SubscriptionId / CustomerId / PlanId") {
        // V-ID1
        it("accepts valid positive value") {
            SubscriptionId(1L).shouldBeRight().value shouldBe 1L
            CustomerId(1L).shouldBeRight().value shouldBe 1L
            PlanId(1L).shouldBeRight().value shouldBe 1L
        }

        // V-ID2
        it("accepts large positive value") {
            SubscriptionId(9999999L).shouldBeRight().value shouldBe 9999999L
        }

        // V-ID3
        it("rejects zero") {
            SubscriptionId(0L).shouldBeLeft()
            CustomerId(0L).shouldBeLeft()
            PlanId(0L).shouldBeLeft()
        }

        // V-ID4
        it("rejects negative") {
            SubscriptionId(-1L).shouldBeLeft()
            CustomerId(-1L).shouldBeLeft()
            PlanId(-1L).shouldBeLeft()
        }
    }

    describe("MetricName") {
        // V-U5
        it("rejects blank metric name") {
            MetricName("").shouldBeLeft()
        }

        it("accepts non-blank metric name") {
            MetricName("api_calls").shouldBeRight().value shouldBe "api_calls"
        }
    }

    describe("IdempotencyKey") {
        // V-U6
        it("rejects blank key") {
            IdempotencyKey("").shouldBeLeft()
        }

        it("accepts non-blank key") {
            IdempotencyKey("req-123").shouldBeRight().value shouldBe "req-123"
        }
    }
})
