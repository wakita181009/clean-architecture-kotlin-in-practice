package com.wakita181009.classic.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(
    name = "usage_records",
    indexes = [
        Index(name = "idx_usage_records_subscription_id", columnList = "subscription_id"),
        Index(name = "idx_usage_records_idempotency_key", columnList = "idempotency_key", unique = true),
    ],
)
class UsageRecord(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false)
    val subscription: Subscription,
    @Column(name = "metric_name", nullable = false)
    val metricName: String,
    @Column(nullable = false)
    val quantity: Int,
    @Column(name = "recorded_at", nullable = false)
    val recordedAt: Instant,
    @Column(name = "idempotency_key", nullable = false, unique = true)
    val idempotencyKey: String,
)
