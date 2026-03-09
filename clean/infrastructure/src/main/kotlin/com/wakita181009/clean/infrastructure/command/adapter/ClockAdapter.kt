package com.wakita181009.clean.infrastructure.command.adapter

import com.wakita181009.clean.application.port.ClockPort
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant

@Component
class ClockAdapter(
    private val clock: Clock = Clock.systemUTC(),
) : ClockPort {
    override fun now(): Instant = Instant.now(clock)
}
