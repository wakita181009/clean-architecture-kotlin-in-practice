package com.wakita181009.classic.repository

import com.wakita181009.classic.model.AddOn
import org.springframework.data.jpa.repository.JpaRepository

interface AddOnRepository : JpaRepository<AddOn, Long> {
    fun findByIdAndActiveTrue(id: Long): AddOn?
}
