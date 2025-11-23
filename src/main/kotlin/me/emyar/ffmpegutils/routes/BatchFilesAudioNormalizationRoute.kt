package me.emyar.ffmpegutils.routes

import io.ktor.http.*
import io.ktor.server.plugins.di.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import me.emyar.ffmpegutils.models.BatchFilesAudioNormalizationRequest
import me.emyar.ffmpegutils.services.audio.BatchFilesAudioNormalizationService
import kotlin.time.measureTime

fun Route.batchFilesAudioNormalizationRoute(): Route =
    post("/audio-normalization/batch") {
        val service = application.dependencies.resolve<BatchFilesAudioNormalizationService>()
        val req = call.receive<BatchFilesAudioNormalizationRequest>()
        val duration = measureTime { service.process(req) }
        call.respond(HttpStatusCode.OK, "Files are processed in $duration")
    }
