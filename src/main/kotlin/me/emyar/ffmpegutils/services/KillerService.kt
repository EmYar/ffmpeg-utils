package me.emyar.ffmpegutils.services

import io.github.oshai.kotlinlogging.KotlinLogging
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

private val log = KotlinLogging.logger {}

class KillerService {
    init {
        Runtime.getRuntime()
            .addShutdownHook(Thread({ cleanUp() }, "killer-service-shutdown-hook"))
    }

    private val processes: Queue<WeakReference<Process>> = ConcurrentLinkedQueue()

    fun registerProcess(process: Process) {
        processes += WeakReference(process)
    }

    fun cleanUp() {
        processes.forEach {
            try {
                it.get()?.destroy()
            } catch (e: Exception) {
                log.error(e) { "Exception during cleanup on shutdown" }
            }
        }
    }
}
