package me.emyar.ffmpegutils.routes

import io.ktor.http.*
import io.ktor.server.plugins.di.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import me.emyar.ffmpegutils.models.SingleFileAudioNormalizationRequest
import me.emyar.ffmpegutils.services.audio.SingleFileAudioNormalizationService
import kotlin.time.measureTime

/**
 * @summary Single file audio normalization
 * @response 200 text/plain OK
 */
fun Route.singleFileAudioNormalizationRoute(): Route =
    post("/audio-normalization/single") {
        val req = call.receive<SingleFileAudioNormalizationRequest>()
        val duration = measureTime {
            application.dependencies.resolve<SingleFileAudioNormalizationService>()
                .process(req)
        }
        call.respond(HttpStatusCode.OK, "Audio for '${req.inputFilePath}' normalized successfully. $duration")
    }
