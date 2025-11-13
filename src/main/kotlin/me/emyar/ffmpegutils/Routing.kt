package me.emyar.ffmpegutils

import io.ktor.server.application.*
import io.ktor.server.routing.*
import kotlinx.coroutines.sync.Mutex
import me.emyar.ffmpegutils.processing.batch.filesBatchRoute
import me.emyar.ffmpegutils.processing.common.fileInfo
import me.emyar.ffmpegutils.processing.single.singleFileRoute

/**
 * Выполняем обработку в порядке очереди в один поток
 */
private val mutex = Mutex()

fun Application.configureAudioNormalizationRouting() {
    routing {
        route(path = "/api") {
            fileInfo()
            singleFileRoute(mutex)
            filesBatchRoute(mutex)
        }
    }
}