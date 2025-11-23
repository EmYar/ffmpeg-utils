package me.emyar.ffmpegutils

import io.ktor.server.application.*
import io.ktor.server.cio.*

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    configureContentNegotiation()
    configureStatusPages()
    configureOpenApi()
    configureAudioNormalizationRouting()
}
