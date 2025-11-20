package me.emyar.ffmpegutils

import io.ktor.server.application.*
import io.ktor.server.routing.*
import kotlinx.coroutines.sync.Semaphore
import me.emyar.ffmpegutils.processing.batch.filesBatchRoute
import me.emyar.ffmpegutils.processing.common.PARALLELISM
import me.emyar.ffmpegutils.processing.common.fileInfo
import me.emyar.ffmpegutils.processing.single.singleFileRoute

private val parallelismSemaphore = Semaphore(PARALLELISM)

fun Application.configureAudioNormalizationRouting() {
    routing {
        route(path = "/api") {
            fileInfo()
            singleFileRoute(parallelismSemaphore)
            filesBatchRoute(parallelismSemaphore)
        }
    }
}
