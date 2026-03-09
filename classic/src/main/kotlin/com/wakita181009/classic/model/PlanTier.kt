package com.wakita181009.classic.model

enum class PlanTier {
    FREE,
    STARTER,
    PROFESSIONAL,
    ENTERPRISE,
    ;

    fun isUpgradeTo(other: PlanTier): Boolean = this.ordinal < other.ordinal
}
