package me.emyar.ffmpegutils

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import io.ktor.server.routing.*
import kotlinx.coroutines.sync.Semaphore
import me.emyar.ffmpegutils.processing.batch.filesBatchRoute
import me.emyar.ffmpegutils.processing.common.DEFAULT_PARALLELISM
import me.emyar.ffmpegutils.processing.common.fileInfo
import me.emyar.ffmpegutils.processing.single.singleFileRoute

private val log = KotlinLogging.logger {}

private val parallelismSemaphore = Semaphore(getParallelism())

fun Application.configureAudioNormalizationRouting() {
    routing {
        route(path = "/api") {
            fileInfo()
            singleFileRoute(parallelismSemaphore)
            filesBatchRoute(parallelismSemaphore)
        }
    }
}

private fun getParallelism(): Int {
    log.info { "Trying to read parallelism limit from PARALLELISM environment variable" }
    val env = System.getenv("PARALLELISM")
        ?: run {
            log.info { "PARALLELISM is not set, using default parallelism = $DEFAULT_PARALLELISM" }
            return DEFAULT_PARALLELISM
        }
    log.info { "Using parallelism = $env" }
    return env.toInt()
}