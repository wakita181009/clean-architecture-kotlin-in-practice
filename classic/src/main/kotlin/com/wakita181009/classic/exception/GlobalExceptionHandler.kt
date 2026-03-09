package com.wakita181009.classic.exception

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {
    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(ServiceException::class)
    fun handle(ex: ServiceException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(ex.status).body(ErrorResponse(ex.message ?: "Unknown error", ex.status.value()))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handle(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message =
            ex.bindingResult.fieldErrors
                .joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        return ResponseEntity.badRequest().body(ErrorResponse(message, HttpStatus.BAD_REQUEST.value()))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handle(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> =
        ResponseEntity.badRequest().body(ErrorResponse(ex.message ?: "Invalid argument", HttpStatus.BAD_REQUEST.value()))

    @ExceptionHandler(IllegalStateException::class)
    fun handle(ex: IllegalStateException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse(ex.message ?: "Invalid state", HttpStatus.CONFLICT.value()))

    @ExceptionHandler(Exception::class)
    fun handle(ex: Exception): ResponseEntity<ErrorResponse> {
        logger.error("Unexpected error", ex)
        return ResponseEntity
            .internalServerError()
            .body(ErrorResponse("Internal server error", HttpStatus.INTERNAL_SERVER_ERROR.value()))
    }
}
