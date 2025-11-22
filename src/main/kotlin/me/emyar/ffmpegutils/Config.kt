package me.emyar.ffmpegutils

import io.github.oshai.kotlinlogging.KotlinLogging
import me.emyar.ffmpegutils.audionormaliz.common.DEFAULT_PARALLELISM
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

    val DEFAULT_BASE_IN_PATH: Path? = let {
        log.info { "Trying to read default input path prefix from DEFAULT_INPUT_PATH_BASE environment variable" }
        val env = System.getenv("DEFAULT_INPUT_PATH_BASE")
            ?: run {
                log.info { "DEFAULT_INPUT_PATH_BASE is not set" }
                return@let null
            }
        log.info { "Using default input path prefix = '$env'" }
        return@let Paths.get(env)
    }

    val DEFAULT_BASE_OUT_PATH: Path? = let {
        log.info { "Trying to read default output path prefix from DEFAULT_OUTPUT_PATH_BASE environment variable" }
        val env = System.getenv("DEFAULT_OUTPUT_PATH_BASE")
            ?: run {
                log.info { "DEFAULT_OUTPUT_PATH_BASE is not set" }
                return@let null
            }
        log.info { "Using default output path prefix = '$env'" }
        return@let Paths.get(env)
    }
}