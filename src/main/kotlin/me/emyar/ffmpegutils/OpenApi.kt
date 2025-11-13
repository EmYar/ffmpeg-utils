package me.emyar.ffmpegutils

import io.ktor.server.application.*
import io.ktor.server.plugins.openapi.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.routing.*

fun Application.configureOpenApi() {
    routing {
        openAPI(path = "/docs", swaggerFile = "openapi/generated.json")
        swaggerUI(path = "/swagger", swaggerFile = "openapi/generated.json")
    }
}