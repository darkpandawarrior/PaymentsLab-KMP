package com.paymentslab.backend

import io.ktor.http.HttpStatusCode

/**
 * Domain exceptions mapped to [com.paymentslab.core.protocol.ApiError] + an HTTP status by the
 * StatusPages plugin. [code] is the stable machine-readable ApiError.code.
 */
sealed class ApiException(
    val status: HttpStatusCode,
    val code: String,
    override val message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class BadRequestException(
    code: String,
    message: String,
    // A 4xx translated from a domain exception keeps that exception as its cause, so the server log
    // still carries the original stack. Without it the translation silently discards the only
    // evidence of what actually failed.
    cause: Throwable? = null,
) : ApiException(HttpStatusCode.BadRequest, code, message, cause)

class NotFoundException(
    code: String,
    message: String,
) : ApiException(HttpStatusCode.NotFound, code, message)

class UnauthorizedException(
    code: String,
    message: String,
) : ApiException(HttpStatusCode.Unauthorized, code, message)
