package me.emyar.ffmpegutils.processing.common

import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Path
import java.nio.file.Paths

private val log = KotlinLogging.logger {}

const val EBU_R128_CONFIG = "I=-23:TP=-2:LRA=7"

val PARALLELISM = getParallelism()

val BASE_IN_PATH: Path = Paths.get(System.getenv("DEFAULT_INPUT_PATH_BASE")!!)
val BASE_OUT_PATH: Path = Paths.get(System.getenv("DEFAULT_OUTPUT_PATH_BASE")!!)

private fun getParallelism(): Int {
    log.info { "Trying to read parallelism limit from PARALLELISM environment variable" }
    val env = System.getenv("PARALLELISM")
        ?: run {
            log.info { "PARALLELISM is not set, using default parallelism = 1" }
            return 1
        }
    log.info { "Using parallelism = $env" }
    return env.toInt()
}