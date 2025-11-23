package me.emyar.ffmpegutils

import io.ktor.server.application.*
import io.ktor.server.routing.*
import me.emyar.ffmpegutils.routes.batchFilesAudioNormalizationRoute
import me.emyar.ffmpegutils.routes.singleFileAudioNormalizationRoute
import me.emyar.ffmpegutils.routes.streamsInfo

fun Application.configureAudioNormalizationRouting() {
    routing {
        route(path = "/api/v1") {
            streamsInfo()
            batchFilesAudioNormalizationRoute()
            singleFileAudioNormalizationRoute()
        }
    }
}
