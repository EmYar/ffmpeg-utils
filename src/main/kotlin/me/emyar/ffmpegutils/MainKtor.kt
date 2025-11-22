package me.emyar.ffmpegutils

import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*

fun main() {
    embeddedServer(CIO, port = 8080, module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    configureContentNegotiation()
    configureStatusPages()
    configureOpenApi()
    configureAudioNormalizationRouting()
}