package me.emyar.ffmpegutils

import io.github.oshai.kotlinlogging.KotlinLogging
import me.emyar.ffmpegutils.processing.common.DEFAULT_PARALLELISM
import java.nio.file.Path
import java.nio.file.Paths

private val log = KotlinLogging.logger {}

object Config {
    val PARALLELISM: Int = let {
        log.info { "Trying to read parallelism limit from PARALLELISM environment variable" }
        val env = System.getenv("PARALLELISM")
            ?: run {
                log.info { "PARALLELISM is not set, using default parallelism = $DEFAULT_PARALLELISM" }
                return@let DEFAULT_PARALLELISM
            }
        log.info { "Using parallelism = $env" }
        return@let env.toInt()
    }
    val BASE_IN_PATH: Path = Paths.get(System.getenv("DEFAULT_INPUT_PATH_BASE") ?: "")
    val BASE_OUT_PATH: Path = Paths.get(System.getenv("DEFAULT_OUTPUT_PATH_BASE") ?: "")
}