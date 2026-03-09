package com.wakita181009.clean.application.port

import java.time.Instant

interface ClockPort {
    fun now(): Instant
}
