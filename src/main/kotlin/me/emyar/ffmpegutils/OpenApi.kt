package me.emyar.ffmpegutils

import io.ktor.server.application.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.routing.*

fun Application.configureOpenApi() {
    routing {
        swaggerUI(path = "/swagger", swaggerFile = "openapi/generated.json")
    }
}