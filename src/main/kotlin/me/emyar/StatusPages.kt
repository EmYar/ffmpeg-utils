package me.emyar

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.requestvalidation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.serialization.SerializationException

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<SerializationException> { call, cause ->
            call.application.environment.log.warn(
                "400 SerializationException at ${call.request.httpMethod.value} ${call.request.uri}: ${cause.message}",
                cause
            )
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf(
                    "error" to "Invalid JSON",
                    "details" to (cause.message ?: "Serialization error")
                )
            )
        }

        exception<ContentTransformationException> { call, cause ->
            call.application.environment.log.warn(
                "400 ContentTransformationException at ${call.request.httpMethod.value} ${call.request.uri}: ${cause.message}",
                cause
            )
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "Bad request body", "details" to cause.message)
            )
        }

        exception<RequestValidationException> { call, cause ->
            call.application.environment.log.warn(
                "Validation failed: ${cause.reasons.joinToString()}"
            )
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "Validation failed", "details" to cause.reasons)
            )
        }

        // общий fallback
        exception<Throwable> { call, cause ->
            call.application.environment.log.error(
                "Unhandled ${call.request.httpMethod.value} ${call.request.uri}: ${cause.message}", cause
            )
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Internal server error")
            )
        }
    }
}