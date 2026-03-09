package com.wakita181009.clean.framework

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan

@SpringBootApplication
@ComponentScan(
    basePackages = [
        "com.wakita181009.clean.infrastructure",
        "com.wakita181009.clean.presentation",
        "com.wakita181009.clean.framework",
    ],
)
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
