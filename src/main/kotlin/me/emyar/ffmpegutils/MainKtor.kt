package me.emyar.ffmpegutils

import io.ktor.server.application.*
import io.ktor.server.cio.*

fun main(args: Array<String>): Unit = EngineMain.main(args)

@Suppress("unused") // application.conf
fun Application.module() {
    configureContentNegotiation()
    configureOpenApi()
    configureAudioNormalizationRouting()
}
