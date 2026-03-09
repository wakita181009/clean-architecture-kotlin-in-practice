package com.wakita181009.clean.domain.model

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class PlanTierTest : DescribeSpec({

    describe("PlanTier ordering") {
        // V-T1
        it("upgrade: STARTER to PROFESSIONAL") {
            PlanTier.PROFESSIONAL.isUpgradeFrom(PlanTier.STARTER) shouldBe true
        }

        // V-T2
        it("downgrade: PROFESSIONAL to STARTER") {
            PlanTier.STARTER.isDowngradeFrom(PlanTier.PROFESSIONAL) shouldBe true
        }

        // V-T3
        it("same tier: STARTER to STARTER") {
            PlanTier.STARTER.isSameTier(PlanTier.STARTER) shouldBe true
        }

        // V-T4
        it("upgrade: FREE to STARTER") {
            PlanTier.STARTER.isUpgradeFrom(PlanTier.FREE) shouldBe true
        }

        // V-T5
        it("downgrade: ENTERPRISE to FREE") {
            PlanTier.FREE.isDowngradeFrom(PlanTier.ENTERPRISE) shouldBe true
        }
    }
})
