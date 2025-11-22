package me.emyar.ffmpegutils.audionormaliz.batch

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.sync.Semaphore

fun Route.filesBatchRoute(parallelismLimiter: Semaphore): Route =
    post("/audio-normalization/batch") {
        val req = call.receive<AudioNormalizationBatchRequest>()
        AudioNormalizationBatchService.process(req, parallelismLimiter)
        call.respond(HttpStatusCode.OK)
    }