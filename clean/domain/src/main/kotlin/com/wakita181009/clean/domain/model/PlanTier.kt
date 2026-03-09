package com.wakita181009.clean.domain.model

enum class PlanTier(val rank: Int) {
    FREE(0),
    STARTER(1),
    PROFESSIONAL(2),
    ENTERPRISE(3),
    ;

    fun isUpgradeFrom(other: PlanTier): Boolean = this.rank > other.rank

    fun isDowngradeFrom(other: PlanTier): Boolean = this.rank < other.rank

    fun isSameTier(other: PlanTier): Boolean = this.rank == other.rank
}
