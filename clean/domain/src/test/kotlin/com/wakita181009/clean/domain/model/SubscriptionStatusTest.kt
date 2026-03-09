package com.wakita181009.clean.domain.model

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Instant

class SubscriptionStatusTest : DescribeSpec({

    val now = Instant.parse("2025-01-15T00:00:00Z")
    val gracePeriodEnd = Instant.parse("2025-01-22T00:00:00Z")

    describe("Valid transitions") {
        // S-V1
        it("Trial -> Active") {
            SubscriptionStatus.Trial.activate().shouldBeRight()
                .shouldBeInstanceOf<SubscriptionStatus.Active>()
        }

        // S-V2
        it("Trial -> Canceled") {
            SubscriptionStatus.Trial.cancel(now).shouldBeRight()
                .shouldBeInstanceOf<SubscriptionStatus.Canceled>()
        }

        // S-V3
        it("Trial -> Expired") {
            SubscriptionStatus.Trial.expire().shouldBeRight()
                .shouldBeInstanceOf<SubscriptionStatus.Expired>()
        }

        // S-V4
        it("Active -> Paused (within limit)") {
            SubscriptionStatus.Active.pause(now, 0).shouldBeRight()
                .shouldBeInstanceOf<SubscriptionStatus.Paused>()
        }

        // S-V5
        it("Active -> PastDue") {
            SubscriptionStatus.Active.markPastDue(gracePeriodEnd).shouldBeRight()
                .shouldBeInstanceOf<SubscriptionStatus.PastDue>()
        }

        // S-V6
        it("Active -> Canceled") {
            SubscriptionStatus.Active.cancel(now).shouldBeRight()
                .shouldBeInstanceOf<SubscriptionStatus.Canceled>()
        }

        // S-V7
        it("Paused -> Active (resume)") {
            SubscriptionStatus.Paused(now).resume().shouldBeRight()
                .shouldBeInstanceOf<SubscriptionStatus.Active>()
        }

        // S-V8
        it("Paused -> Canceled") {
            SubscriptionStatus.Paused(now).cancel(now).shouldBeRight()
                .shouldBeInstanceOf<SubscriptionStatus.Canceled>()
        }

        // S-V10
        it("PastDue -> Active (recover)") {
            SubscriptionStatus.PastDue(gracePeriodEnd).recover().shouldBeRight()
                .shouldBeInstanceOf<SubscriptionStatus.Active>()
        }

        // S-V11
        it("PastDue -> Canceled") {
            SubscriptionStatus.PastDue(gracePeriodEnd).cancel(now).shouldBeRight()
                .shouldBeInstanceOf<SubscriptionStatus.Canceled>()
        }
    }

    describe("Invalid transitions") {
        // S-V4 with pause limit
        it("Active -> Paused fails when pause limit reached") {
            SubscriptionStatus.Active.pause(now, 2).shouldBeLeft()
        }

        // S-I12: Canceled is terminal (no methods available except name)
        it("Canceled has no transition methods") {
            val canceled = SubscriptionStatus.Canceled(now)
            canceled.name shouldBe "CANCELED"
            // compile-time safety: no transition methods available on Canceled
        }

        // S-I13: Expired is terminal
        it("Expired has no transition methods") {
            val expired = SubscriptionStatus.Expired
            expired.name shouldBe "EXPIRED"
        }
    }

    describe("State properties") {
        it("Paused carries pausedAt") {
            val paused = SubscriptionStatus.Active.pause(now, 0).shouldBeRight()
            (paused as SubscriptionStatus.Paused).pausedAt shouldBe now
        }

        it("PastDue carries gracePeriodEnd") {
            val pastDue = SubscriptionStatus.Active.markPastDue(gracePeriodEnd).shouldBeRight()
            (pastDue as SubscriptionStatus.PastDue).gracePeriodEnd shouldBe gracePeriodEnd
        }

        it("Canceled carries canceledAt") {
            val canceled = SubscriptionStatus.Active.cancel(now).shouldBeRight()
            (canceled as SubscriptionStatus.Canceled).canceledAt shouldBe now
        }
    }
})
