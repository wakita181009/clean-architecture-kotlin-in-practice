package com.wakita181009.classic.exception

import org.springframework.http.HttpStatus

open class ServiceException(
    message: String,
    val status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR,
) : RuntimeException(message)
