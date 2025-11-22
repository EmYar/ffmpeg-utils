package me.emyar.ffmpegutils.audionormaliz.single

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.sync.Semaphore
import kotlin.time.measureTime

/**
 * Normalize audio for in one file
 *
 * @summary Обработка одного файла
 * @response 200 text/plain OK
 */
fun Route.singleFileRoute(parallelismLimiter: Semaphore): Route =
    post("/audio-normalization/single") {
        val req = call.receive<AudioNormalizationRequestSingle>()
        val duration = measureTime {
            AudioNormalizationSingleService.process(req, parallelismLimiter)
        }
        call.respond(HttpStatusCode.OK, "Audio for '${req.inputFilePath}' normalized successfully. $duration")
    }
