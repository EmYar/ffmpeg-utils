package me.emyar.ffmpegutils

import io.ktor.server.application.*
import io.ktor.server.routing.*
import kotlinx.coroutines.sync.Semaphore
import me.emyar.ffmpegutils.audionormaliz.batch.filesBatchRoute
import me.emyar.ffmpegutils.audionormaliz.common.fileInfo
import me.emyar.ffmpegutils.audionormaliz.single.singleFileRoute

private val parallelismSemaphore = Semaphore(Config.PARALLELISM)

fun Application.configureAudioNormalizationRouting() {
    routing {
        route(path = "/api") {
            fileInfo()
            singleFileRoute(parallelismSemaphore)
            filesBatchRoute(parallelismSemaphore)
        }
    }
}
