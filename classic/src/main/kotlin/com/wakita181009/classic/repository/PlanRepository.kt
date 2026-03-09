package com.wakita181009.classic.repository

import com.wakita181009.classic.model.Plan
import org.springframework.data.jpa.repository.JpaRepository

interface PlanRepository : JpaRepository<Plan, Long> {
    fun findByIdAndActiveTrue(id: Long): Plan?
}
